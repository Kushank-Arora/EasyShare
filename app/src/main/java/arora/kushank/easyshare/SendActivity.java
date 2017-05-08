package arora.kushank.easyshare;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

public class SendActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener {

    //constants
    private static final String FILENAME = "easyShare";
    private static final String ACC_NAME = "name";
    final static String TAG = "SendActivity";
    private static final String FILE_ADDRESS = "file_add";

    //Views
    SwipeRefreshLayout swipeRefreshLayout;
    WifiManager wm;
    ListView lvConnDevices;
    TextView tvMessage;

    private ArrayList<DeviceIpName> listConnDevices;
    ArrayAdapter<String> arrayListAdapter;
    private static final int SERVER_PORT = 5000;
    private AsyncTask<Void, Void, Void> talkServer;
    private SharedPreferences sharedData;

    //Control Variables
    boolean isHotspot;
    private boolean justCancelTalkServer;
    private int InsertConnectedTodo;
    boolean isRefreshing;

    //Communication Variable
    ArrayList<String> filesToBeSent;
    private String MyIP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        Intent gotIntent=getIntent();
        final Bundle myBasket = gotIntent.getExtras();
        filesToBeSent = myBasket.getStringArrayList(FILE_ADDRESS);

        if(filesToBeSent==null){
            ClipData clipData = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                clipData = gotIntent.getClipData();
            }
            filesToBeSent = new ArrayList<>();
            if (clipData == null) {
                String filename = gotIntent.getData().getPath();
                filename=SendOrRecieve.modifyIfInvalid(this,filename,gotIntent.getData());
                filesToBeSent.add(filename);
            } else {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    String filename = clipData.getItemAt(i).getUri().getPath();
                    filename=SendOrRecieve.modifyIfInvalid(this,filename,clipData.getItemAt(i).getUri());
                    filesToBeSent.add(filename);
                }
            }
        }

        initViews();
        setListeners();

        sharedData = getSharedPreferences(FILENAME, MODE_PRIVATE);
        wm = (WifiManager) getSystemService(WIFI_SERVICE);

        listConnDevices = new ArrayList<>();
        arrayListAdapter = new ArrayAdapter<>(this, R.layout.list_item_white, getNameFromDevice(listConnDevices));
        lvConnDevices.setAdapter(arrayListAdapter);
        lvConnDevices.setOnItemClickListener(this);
    }

    private void setListeners() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this);
        }
    }

    private void initViews() {
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshSendActivity);
        lvConnDevices = (ListView) findViewById(R.id.lvSendActivity);
        tvMessage = (TextView) findViewById(R.id.tvMessage);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG,"OnResumeCalled");
        justCancelTalkServer = false;
        isRefreshing = false;

        swipeRefreshLayout.setRefreshing(true);
        doThisOnRefresh();
        if (!isHotspot) {
            try {
                talkServer = new TalkServer();
                talkServer.execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG,"OnPauseCalled");
        if (talkServer != null) {
            Log.d(TAG, "Trying to cancel");
            justCancelTalkServer = true;
        }
    }

    @Override
    public void onRefresh() {
        Log.d(TAG, "onRefreshStarted");
        doThisOnRefresh();
    }

    @SuppressWarnings("deprecation")
    private void doThisOnRefresh() {
        Log.d(TAG, "doThisOnRefresh");

        tvMessage.setText("Searching...");

        if (isRefreshing)
            return;
        else
            isRefreshing = true;
        MyIP = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        isHotspot = MyIP.trim().equals("0.0.0.0");

        if (isHotspot) {
            MyIP="192.168.43.1";
            Toast.makeText(this, "My IP: " + MyIP, Toast.LENGTH_SHORT).show();

            justCancelTalkServer = true;
            //BackgroundTask to get App Connected Devices.
            getConnectedClientList(getClientList());
        } else {
            justCancelTalkServer = true;
            new RefreshServerRequest().execute();
            swipeRefreshLayout.setRefreshing(false);
            isRefreshing = false;
            tvMessage.setText(R.string.send_message_head);
        }
    }

    /**
     * Gets a list of the clients connected to the Hotspot
     */
    public ArrayList<String> getClientList() {

        Log.d(TAG, "getClientList");
        BufferedReader br = null;
        ArrayList<String> result = null;

        try {
            result = new ArrayList<>();
            br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line;//
            br.readLine();
            while ((line = br.readLine()) != null) {
                String temp = line.substring(0, 14).split(" ")[0];
                System.out.println(temp);
                result.add(temp);
            }
        } catch (Exception e) {
            e.printStackTrace();
            //Log.e(this.getClass().toString(), e.getMessage());
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (IOException e) {
                e.printStackTrace();
                //Log.e(this.getClass().toString(), e.getMessage());
            }
        }

        return result;
    }


    private void getConnectedClientList(ArrayList<String> listDevices) {

        Log.d(TAG, "getConnectedClientList");
        String myName = sharedData.getString(ACC_NAME, "None");
        DeviceIpName myDevice = new DeviceIpName("192.168.43.1", myName);
        listConnDevices = new ArrayList<>();
        listConnDevices.add(myDevice);

        arrayListAdapter.clear();
        //arrayListAdapter.addAll(getNameFromDevice(listConnDevices));
        arrayListAdapter.notifyDataSetChanged();

        InsertConnectedTodo = listDevices.size();

        for (String device : listDevices) {
            new InsertConnected(device).execute();
        }
    }

    private ArrayList<String> getNameFromDevice(ArrayList<DeviceIpName> listConnDevices) {
        ArrayList<String> newArrayList = new ArrayList<>();
        for (DeviceIpName device : listConnDevices)
            if(!device.ipAddress.equals(MyIP))
                newArrayList.add(device.name);
        return newArrayList;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        justCancelTalkServer = true;
//        for(int i=0;i<filesToBeSent.size();i++)
        for(int i=0;i<listConnDevices.size();i++)
        {
            if(listConnDevices.get(i).name.equals(arrayListAdapter.getItem(position))) {
                new ClientThreadAsync(listConnDevices.get(i).ipAddress, 0).execute();
                return;
            }
        }
    }

    class InsertConnected extends AsyncTask<Void, Void, Void> {

        Socket serverSocket;

        String ipAddress;
        private boolean added;

        public InsertConnected(String device) {
            Log.d(TAG, "InsertConnected");
            ipAddress = device;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Log.d(TAG, "InsertConnectedPre");
            added = false;
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "InsertConnectedDoInBG");
            try {
                InetAddress server_Add = InetAddress.getByName(ipAddress);
                serverSocket = new Socket(server_Add, SERVER_PORT);
                serverSocket.setSoTimeout(1000);

                System.out.println("connecting to " + ipAddress);

                DataOutputStream out = new DataOutputStream(serverSocket.getOutputStream());
                out.writeUTF(Messages.GET_NAME);

                DataInputStream in = new DataInputStream(serverSocket.getInputStream());
                String name = in.readUTF();
                DeviceIpName newobj = new DeviceIpName(ipAddress, name);

                boolean alreadyPresent = false;
                for (DeviceIpName temp : listConnDevices) {
                    if (temp.ipAddress.equals(newobj.ipAddress)) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent) {
                    listConnDevices.add(newobj);
                    added = true;
                }

            } catch (IOException e1) {
                System.out.println("Could'nt connect to " + ipAddress);
            }
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(TAG, "onPostExecuteCalledInsertConnected");

            //lvConnDevices.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, listConnDevices));
            arrayListAdapter.clear();

            arrayListAdapter.addAll(getNameFromDevice(listConnDevices));
            arrayListAdapter.notifyDataSetChanged();

            if (added) {
                new SendSibling(listConnDevices).execute();
            }

            InsertConnectedTodo--;
            if (InsertConnectedTodo == 0) {
                swipeRefreshLayout.setRefreshing(false);
                isRefreshing = false;
                tvMessage.setText(R.string.send_message_head);
                justCancelTalkServer = false;
                try {
                    talkServer = new TalkServer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                talkServer.execute();
            }
        }
    }


    class SendSibling extends AsyncTask<Void, Void, Void> {

        Socket serverSocket;

        ArrayList<DeviceIpName> Addresses;

        public SendSibling(ArrayList<DeviceIpName> device) {
            Log.d(TAG, "SendSiblingClass");
            Addresses = device;
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "SendSiblingClassDoInBG");

            for (DeviceIpName sibling : Addresses) {
                String ipAddress = sibling.ipAddress;
                try {
                    InetAddress server_Add = InetAddress.getByName(ipAddress);
                    serverSocket = new Socket(server_Add, SERVER_PORT);
                    serverSocket.setSoTimeout(1000);

                    System.out.println("Connecting to " + ipAddress);

                    DataOutputStream out = new DataOutputStream(serverSocket.getOutputStream());
                    out.writeUTF(Messages.CATCH_SIBLING);

                    //Send the number of siblings.
                    out.writeUTF(Addresses.size() + "");

                    //Send the siblings.
                    for (DeviceIpName sib : Addresses) {
                        out.writeUTF(sib.toString());
                    }

                } catch (ConnectException e) {
                    System.out.println("Could'nt connect to " + ipAddress);
                    //e.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                try {
                    if (serverSocket != null)
                        serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(TAG, "onPostExecuteCalledSendSiblingConnected");
        }
    }

    class RefreshServerRequest extends AsyncTask<Void, Void, Void> {

        Socket serverSocket;

        public RefreshServerRequest() {
            Log.d(TAG, "RefreshServerRequest");
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "RefreshServerRequestDoInBG");

            String ipAddress = "192.168.43.1";
            try {
                InetAddress server_Add = InetAddress.getByName(ipAddress);
                serverSocket = new Socket(server_Add, SERVER_PORT);
                serverSocket.setSoTimeout(1000);

                System.out.println("Connecting to " + ipAddress);

                DataOutputStream out = new DataOutputStream(serverSocket.getOutputStream());
                out.writeUTF(Messages.PLEASE_REFRESH);

            } catch (ConnectException e) {
                System.out.println("Could'nt connect to " + ipAddress);
                //e.printStackTrace();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(TAG, "onPostExecuteCalledSendSiblingConnected");
            justCancelTalkServer = false;
            try {
                talkServer = new TalkServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
            talkServer.execute();
        }
    }

    static class Messages {
        public static final String GET_NAME = "get_name";
        public static final String CATCH_SIBLING = "catch_sibling";
        public static final String PLEASE_REFRESH = "please_refresh";
        public static final String CATCH_FILE = "catch_file";
    }

    class ClientThreadAsync extends AsyncTask<Void, Integer, Void> {

        private final String fileToBeSent;
        String toIp;
        private Socket serverSocket;
        private ProgressDialog dialog;
        boolean gotError;
        int fileNo;

        public ClientThreadAsync(String toIp, int fileNo) {
            Log.d(TAG, "ClientThreadAsync");
            this.toIp = toIp;
            serverSocket = null;
            this.fileToBeSent=filesToBeSent.get(fileNo);
            this.fileNo=fileNo;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            dialog=new ProgressDialog(SendActivity.this);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setMax(100);
            dialog.setTitle("Fetching Data...");
            dialog.show();
            gotError=false;
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "ClientThreadAsyncDoInBG");
            try {
                InetAddress server_Add = InetAddress.getByName(toIp);
                serverSocket = new Socket(server_Add, 5000);

                System.out.println("conn set");

                DataOutputStream out = new DataOutputStream(serverSocket.getOutputStream());
                out.writeUTF(Messages.CATCH_FILE);
                String[] parts=fileToBeSent.split("/");
                out.writeUTF(parts[parts.length-1]);

                File myFile=new File(fileToBeSent);
                long fileSize=myFile.length();
                out.writeUTF(fileSize+"");
                Log.d(TAG,fileSize+"");
                FileInputStream fis=new FileInputStream(fileToBeSent);
                BufferedInputStream bis=new BufferedInputStream(fis);
                byte b[]=new byte[1024*1024];
                int count;
                int progress=0;
                while((count=bis.read(b,0,b.length))>0){
                    out.write(b,0,count);
                    progress+=count;
                    publishProgress((int)(progress/(float)fileSize*100));
                }
                out.flush();
                out.close();
            } catch (IOException e1) {
                e1.printStackTrace();
                gotError=true;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            dialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            Log.d(TAG, "onPostExecuteClientThread");
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            dialog.dismiss();
            if(gotError) {
                new ClientThreadAsync(toIp,fileNo).execute();
            }
            else if(fileNo+1<filesToBeSent.size())
                new ClientThreadAsync(toIp,fileNo+1).execute();
            else{
                Snackbar.make(findViewById(R.id.lvSendActivity),"Files Sent Successfully!!",Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    public class TalkServer extends AsyncTask<Void, Void, Void> {
        private ServerSocket serverSocket = null;
        Socket server = null;
        String msg;

        public TalkServer() throws IOException {
            Log.d(TAG, "TalkServer");
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.d(TAG, "TalkServerPreExecute");
            //listConnDevices = new ArrayList<>();
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                serverSocket.setSoTimeout(1000);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(SendActivity.this,"Please Restart the App",Toast.LENGTH_LONG).show();
            }


        }

        @Override
        protected Void doInBackground(Void... params) {
            server = null;
            loop:
            while (!justCancelTalkServer) {
                try {
                    System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "...");
                    server = serverSocket.accept();
                    System.out.println("Just connected to " + server.getRemoteSocketAddress());
                    DataInputStream in = new DataInputStream(server.getInputStream());
                    msg = in.readUTF();
                    System.out.println(msg);
                    switch (msg) {
                        case Messages.GET_NAME:
                            DataOutputStream out = new DataOutputStream(server.getOutputStream());
                            out.writeUTF(sharedData.getString(ACC_NAME, "None"));
                            out.close();
                            break;
                        case Messages.CATCH_SIBLING:
                            int numSibling = Integer.parseInt(in.readUTF());
                            System.out.println("NumSibling:" + numSibling);
                            listConnDevices.clear();
                            for (int i = 0; i < numSibling; i++) {
                                String res = in.readUTF();
                                String parts[] = res.split("#");
                                listConnDevices.add(new DeviceIpName(parts[0], parts[1]));
                            }
                            break loop;
                        case Messages.PLEASE_REFRESH:
                            break loop;
                    }
                } catch (SocketTimeoutException s) {
                    System.out.println("Socket timed out!");
                    //break;
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                try {
                    if (server != null)
                        server.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            try {
                if (serverSocket != null)
                    serverSocket.close();
                if (server != null)
                    server.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(TAG, "onPostExecuteTalkServer");
            if (msg != null && msg.equals(Messages.CATCH_SIBLING)) {

                //ArrayAdapter<DeviceIpName> ad = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, arrayList);
                //lvConnDevices.setAdapter(ad);
                arrayListAdapter.clear();
                arrayListAdapter.addAll(getNameFromDevice(listConnDevices));
                arrayListAdapter.notifyDataSetChanged();

                try {
                    talkServer = new TalkServer();
                    talkServer.execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (msg != null && msg.equals(Messages.PLEASE_REFRESH)) {
                if (!swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(true);
                    doThisOnRefresh();
                }
            }
        }
    }
}
