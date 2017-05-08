package arora.kushank.easyshare;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

public class ReceiveActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener {

    //constants
    private static final String FILENAME = "easyShare";
    private static final String ACC_NAME = "name";
    final static String TAG = "ReceiveActivity";
    private static final String FILE_ADDRESS = "file_add";
    private String defaultPath;

    //Views
    SwipeRefreshLayout swipeRefreshLayout;
    WifiManager wm;
    ListView lvReceivedFiles;
    TextView tvMessage;

    private ArrayList<DeviceIpName> listConnDevices;
    ArrayAdapter<String> arrayListAdapter;
    private static final int SERVER_PORT = 5000;
    private AsyncTask<Void, Integer, Void> talkServer;
    private SharedPreferences sharedData;

    //Control Variables
    boolean isHotspot;
    private boolean justCancelTalkServer;
    private int InsertConnectedTodo;
    boolean isRefreshing;

    //Communication Variable
    String fileToBeSent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);

        defaultPath=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();

        initViews();
        setListeners();

        sharedData = getSharedPreferences(FILENAME, MODE_PRIVATE);
        wm = (WifiManager) getSystemService(WIFI_SERVICE);

        listConnDevices = new ArrayList<>();
        arrayListAdapter = new ArrayAdapter<>(this, R.layout.list_item_white, getNameFromDevice(listConnDevices));
        lvReceivedFiles.setAdapter(arrayListAdapter);
        lvReceivedFiles.setOnItemClickListener(this);
    }

    private void setListeners() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this);
        }
    }

    private void initViews() {
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshReceiveActivity);
        lvReceivedFiles = (ListView) findViewById(R.id.lvReceiveActivity);
        tvMessage = (TextView) findViewById(R.id.tvReceiveMessage);
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
        if (!isHotspot) {
            justCancelTalkServer = true;
            new RefreshServerRequest().execute();
        }
    }

    @SuppressWarnings("deprecation")
    private void doThisOnRefresh() {
        Log.d(TAG, "doThisOnRefresh");

        if (isRefreshing)
            return;
        else
            isRefreshing = true;
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        Toast.makeText(this, "My IP: " + ip, Toast.LENGTH_SHORT).show();

        isHotspot = ip.trim().equals("0.0.0.0");

        if (isHotspot) {
            justCancelTalkServer = true;
            //BackgroundTask to get App Connected Devices.
            getConnectedClientList(getClientList());
        } else {
            swipeRefreshLayout.setRefreshing(false);
            isRefreshing = false;
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

        InsertConnectedTodo = listDevices.size();

        for (String device : listDevices) {
            new InsertConnected(device).execute();
        }

    }

    private ArrayList<String> getNameFromDevice(ArrayList<DeviceIpName> listConnDevices) {
        ArrayList<String> newArrayList = new ArrayList<>();
        for (DeviceIpName device : listConnDevices)
            newArrayList.add(device.name);
        return newArrayList;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        File file=new File(defaultPath+"/"+arrayListAdapter.getItem(position));
        Uri path=Uri.fromFile(file);

        //Get File Extension
        String parts[] = arrayListAdapter.getItem(position).replace('.','/').split("/");
        String exx=parts[parts.length-1];

        //Get File MIME type
        String type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(exx);

        //Open the file
        Intent fileOpenIntent=new Intent(Intent.ACTION_VIEW);
        fileOpenIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        fileOpenIntent.setDataAndType(path,type);
        startActivity(fileOpenIntent);
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

            if (added) {
                new SendSibling(listConnDevices).execute();
            }

            InsertConnectedTodo--;
            if (InsertConnectedTodo == 0) {
                swipeRefreshLayout.setRefreshing(false);
                isRefreshing = false;
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

    class ClientThreadAsync extends AsyncTask<Void, Void, Void> {

        String toIp;
        private Socket serverSocket;

        public ClientThreadAsync(String toIp) {
            Log.d(TAG, "ClientThreadAsync");
            this.toIp = toIp;
            serverSocket = null;
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
                out.close();

            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return null;
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
        }
    }

    public class TalkServer extends AsyncTask<Void, Integer, Void> {
        private ServerSocket serverSocket = null;
        Socket server = null;
        ArrayList<DeviceIpName> arrayList;
        String msg;
        private String fileReceived;
        private ProgressDialog dialog;
        private Integer oldprogress;

        public TalkServer() throws IOException {
            Log.d(TAG, "TalkServer");
            oldprogress=0;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.d(TAG, "TalkServerPreExecute");
            arrayList = new ArrayList<>();
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                serverSocket.setSoTimeout(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            dialog=new ProgressDialog(ReceiveActivity.this);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setMax(100);
            dialog.setTitle("Fetching Data...");

        }

        @Override
        protected Void doInBackground(Void... params) {
            server = null;
            loop:
            while (!justCancelTalkServer) {
                try {
                    //System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "...");
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
                            for (int i = 0; i < numSibling; i++) {
                                String res = in.readUTF();
                                String parts[] = res.split("#");
                                arrayList.add(new DeviceIpName(parts[0], parts[1]));
                            }
                            break loop;
                        case Messages.PLEASE_REFRESH:
                            break loop;
                        case Messages.CATCH_FILE:
                            fileReceived=in.readUTF();
                            int fileSize= (int) Long.parseLong(in.readUTF());
                            File f=new File(defaultPath+"/"+fileReceived);
                            Log.d(TAG,fileSize+"");
                            f.delete();
                            if(f.createNewFile())
                                Log.d(TAG,"File Created "+fileReceived);
                            else
                                Log.d(TAG,"File Creation Failed");

                            byte myByteArray[]=new byte[in.available()];

                            FileOutputStream os=new FileOutputStream(f);
                            BufferedOutputStream bos=new BufferedOutputStream(os);

                            int bytesRead;
                            //= in.read(myByteArray, 0, myByteArray.length);
                            //bos.write(myByteArray,0,bytesRead);
                            int current=0;
                            //= bytesRead;
                            //Log.d(TAG,bytesRead+"");

                            do{
                                bytesRead = in.read(myByteArray,0,myByteArray.length);
                                if(bytesRead>0) {
                                    bos.write(myByteArray, 0, bytesRead);
                                    current += bytesRead;
                                }
                                publishProgress((int)(current/(float)fileSize*100));
                                //Log.d(TAG,current+"");
                            }while (bytesRead>0);

                            //bos.write(myByteArray,0,current);
                            bos.flush();
                            bos.close();
                            break loop;
                    }
                } catch (SocketTimeoutException s) {
                    //System.out.println("Socket timed out!");
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
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if(!dialog.isShowing())
                dialog.show();
            oldprogress=values[0];
            //Log.d(TAG+"oldprog",oldprogress+"");
            dialog.setProgress(oldprogress);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(TAG, "onPostExecuteTalkServer");
            if(msg==null)
                return;

            switch(msg){
                case Messages.CATCH_SIBLING:
                    try {
                        talkServer = new TalkServer();
                        talkServer.execute();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case Messages.PLEASE_REFRESH:
                    if (!swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(true);
                        doThisOnRefresh();
                    }
                    break;
                case Messages.CATCH_FILE:
                    dialog.dismiss();
                    arrayListAdapter.remove(fileReceived);
                    arrayListAdapter.add(fileReceived);
                    arrayListAdapter.notifyDataSetChanged();
                    try {
                        talkServer = new TalkServer();
                        talkServer.execute();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    MediaScannerConnection.scanFile(ReceiveActivity.this,
                            new String[]{defaultPath+"/"+fileReceived},
                            null,
                            new MediaScannerConnection.OnScanCompletedListener() {
                                @Override
                                public void onScanCompleted(String path, Uri uri) {
                                    Log.d(TAG, "ScanFinished ");
                                }
                            });

                    break;
            }
        }
    }
}
