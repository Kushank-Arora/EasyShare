package arora.kushank.easyshare;

import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String FILENAME = "easyShare";
    private static final String ACC_NAME = "name";
    final static String TAG = "MainActivity";
    TextView tv;
    EditText et;
    ListView lvConnDevices;
    WifiManager wm;
    private static final int SERVER_PORT = 5000;
    private static final String SERVER_IP = "192.168.43.249";
    private ArrayList<DeviceIpName> listConnDevices;
    ArrayAdapter<DeviceIpName> arrayListAdapter;
    SwipeRefreshLayout swipeRefreshLayout;
    private AsyncTask<Void, Void, Void> talkServer;
    boolean isHotspot;
    private boolean justCancelTalkServer;
    private int InsertConnectedTodo;
    private SharedPreferences sharedData;
    boolean isRefreshing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedData=getSharedPreferences(FILENAME,MODE_PRIVATE);
        justCancelTalkServer = false;
        tv = (TextView) findViewById(R.id.tv);
        et = (EditText) findViewById(R.id.et);
        lvConnDevices = (ListView) findViewById(R.id.lvConncectedDevices);

        wm = (WifiManager) getSystemService(WIFI_SERVICE);

        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefresh);
        if (swipeRefreshLayout != null) {
            //swipeRefreshLayout.setOnRefreshListener(this);
        }
        Log.d(TAG, "OnCreateFinished");
        listConnDevices=new ArrayList<>();
        arrayListAdapter= new ArrayAdapter<>(MainActivity.this, R.layout.list_item_white, listConnDevices);
        lvConnDevices.setAdapter(arrayListAdapter);
        isRefreshing=false;
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResumeStarted");
        super.onResume();
        justCancelTalkServer = false;
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
        Log.d(TAG, "onPauseStarted");
        super.onPause();
        if (talkServer != null) {
            Log.d(TAG, "Trying to cancel");
            justCancelTalkServer = true;
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



    public void onRefresh() {
        Log.d(TAG, "onRefreshStarted");
        doThisOnRefresh();
        if(!isHotspot) {
            justCancelTalkServer=true;
            new RefreshServerRequest().execute();
        }
    }

    @SuppressWarnings("deprecation")
    private void doThisOnRefresh() {
        Log.d(TAG, "doThisOnRefresh");
        if(isRefreshing)
            return;
        else
            isRefreshing=true;
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        tv.setText(ip);
        isHotspot = ip.trim().equals("0.0.0.0");

        if (isHotspot) {
            justCancelTalkServer=true;

            //BackgroundTask to get App Connected Devices.
            getConnectedClientList(getClientList());
        } else {
            swipeRefreshLayout.setRefreshing(false);
            isRefreshing=false;
        }
    }

    private void getConnectedClientList(ArrayList<String> listDevices) {

        Log.d(TAG, "getConnectedClientList");
        String myName=sharedData.getString(ACC_NAME,"None");
        DeviceIpName myDevice=new DeviceIpName("192.168.43.1",myName);
        listConnDevices = new ArrayList<>();
        listConnDevices.add(myDevice);
        arrayListAdapter.clear();
        arrayListAdapter.addAll(listConnDevices);
        arrayListAdapter.notifyDataSetChanged();

        InsertConnectedTodo=listDevices.size();

        for (String device : listDevices) {
            new InsertConnected(device).execute();
        }

    }

    class InsertConnected extends AsyncTask<Void, Void, Void>{

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
                for(DeviceIpName temp:listConnDevices){
                    if(temp.ipAddress.equals(newobj.ipAddress)) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if(!alreadyPresent) {
                    listConnDevices.add(newobj);
                    added=true;
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
        protected void onPostExecute(Void aVoid){
            super.onPostExecute(aVoid);
            Log.d(TAG, "onPostExecuteCalledInsertConnected");

            //lvConnDevices.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, listConnDevices));
            arrayListAdapter.clear();
            arrayListAdapter.addAll(listConnDevices);
            arrayListAdapter.notifyDataSetChanged();

            if(added){
                new SendSibling(listConnDevices).execute();
            }

            InsertConnectedTodo--;
            if(InsertConnectedTodo==0) {
                swipeRefreshLayout.setRefreshing(false);
                isRefreshing=false;
                justCancelTalkServer=false;
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
            justCancelTalkServer=false;
            try {
                talkServer=new TalkServer();
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

    }

    class ClientThreadAsync extends AsyncTask<Void, Void, Void> {

        String data;
        private Socket serverSocket;

        public ClientThreadAsync(String s) {
            Log.d(TAG, "ClientThreadAsync");
            data = s;
            serverSocket=null;
        }


        @Override
        protected Void doInBackground(Void... params) {

            Log.d(TAG, "ClientThreadAsyncDoInBG");
            if(false)
            try {
                InetAddress server_Add = InetAddress.getByName(SERVER_IP);
                //TODO
                //Deactivated this
                serverSocket = new Socket(server_Add, 0);

                System.out.println("conn set");

                DataOutputStream out = new DataOutputStream(serverSocket.getOutputStream());
                out.writeUTF(data + " from " + serverSocket.getLocalSocketAddress());
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
                if(serverSocket!=null)
                    serverSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public class TalkServer extends AsyncTask<Void, Void, Void> {
        private ServerSocket serverSocket = null;
        Socket server = null;
        ArrayList<DeviceIpName> arrayList;
        String msg;

        public TalkServer() throws IOException {
            Log.d(TAG, "TalkServer");
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


        }

        @Override
        protected Void doInBackground(Void... params) {
            server = null;
            loop:while (!justCancelTalkServer) {
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
                            out.writeUTF(sharedData.getString(ACC_NAME,"None"));
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
                arrayListAdapter.addAll(arrayList);
                arrayListAdapter.notifyDataSetChanged();

                try {
                    talkServer = new TalkServer();
                    talkServer.execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else if(msg!=null && msg.equals(Messages.PLEASE_REFRESH)){
                if(!swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(true);
                    doThisOnRefresh();
                }
            }
        }
    }
}
