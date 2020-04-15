package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */

public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    //static final String[] REMOTE_PORTS = {"11108","11112","11116","11120","11124"};
    static final int SERVER_PORT = 10000;

    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    ContentValues values = new ContentValues();
    private Uri mUri;
    ContentResolver contentResolver;

    int counter;
    int proposed_sequence = 0;
    int agreed_sequence;
    int alive = 5;
    int final_sequence = -1000;
    int global_proposedNum;
    ArrayList proposed = new ArrayList( );

    HashMap<Integer, Socket> socket_map  = new HashMap<Integer, Socket>();


    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    private int find_maximum(ArrayList arr) {
        int m = (Integer)arr.get( 0 );
        for (int i = 0 ; i < arr.size(); i++) {
            if  ( ((Integer)(arr.get( i )) > m)) {
                m = (Integer)(arr.get( i ));

            }
        }

        return m;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        counter = 0;

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor( AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
        String scheme = "content";
        String authority = "edu.buffalo.cse.cse486586.groupmessenger2.provider";
        mUri = buildUri(scheme, authority);
        contentResolver = getContentResolver();
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        final EditText editText = (EditText) findViewById(R.id.editText1);
        findViewById( R.id.button4 ).setOnClickListener( new View.OnClickListener( ) {
            @Override
            public void onClick( View v ){
                String msg = editText.getText().toString() + "\n";
                editText.setText("");
                TextView localTextView = (TextView) findViewById(R.id.textView1);
                localTextView.append("\t" + msg);
                TextView remoteTextView = (TextView) findViewById(R.id.textView1);
                remoteTextView.append("\n");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                return;
            }
        });
    }
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Log.i("in server ", "2");
            try {
                while (true) {
                    Socket soc = serverSocket.accept();
                    BufferedReader serverMessage = new BufferedReader( new InputStreamReader( soc.getInputStream( ) ) );
                    String msg = serverMessage.readLine( );
                    Log.i( "in server ", msg );
                    PrintWriter out = new PrintWriter( soc.getOutputStream( ), true );
                    Log.i( "prop seq " , Integer.toString( proposed_sequence ) );
                    Log.i( "fin seq " , Integer.toString( final_sequence ) );
                    if (final_sequence >= proposed_sequence ) {
                        proposed_sequence = final_sequence + 1;
                    }
                    out.println( proposed_sequence );


                    publishProgress( msg );
                    String temp;


                    // for agreed sequence
                    while ( (temp = serverMessage.readLine()) != null ){
                        try {
                            final_sequence = Integer.parseInt( temp );
                            values.put( VALUE_FIELD, msg );
                            values.put( KEY_FIELD, temp );
                            contentResolver.insert( mUri, values );
                            soc.close( );
                            break;
                        }
                        catch (Exception e) {
                            continue;
                        }
                    }
                }
            }catch (Exception e) {
                Log.e(TAG, "Can't make a connection");
                return null;
            }
        }

        protected void onProgressUpdate(String...strings) {

            Log.i("in server ", "2");
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append("\n");
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            Log.i("in client task ",msgs[0]);
            Log.i("in client task ",msgs[1]);
           int  si = 0;
            String[] REMOTE_PORTS = {"11108","11112","11116","11120","11124"};
            try {
                for(String port : REMOTE_PORTS){
                    Socket socket = new Socket( InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port));
                    Log.i( "socket ", socket.toString() );
                    String m;
                    socket_map.put(si, socket  );
                    si += 1;
                    String msgToSend = msgs[0];
                    try {
                        PrintWriter out = new PrintWriter( socket.getOutputStream( ), true );
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        Log.i("in client ", "1");
                        out.println( msgToSend );
                        try{
                            // get proposed number
                            //if null decrease alive
                            m = in.readLine();
                            global_proposedNum = Integer.parseInt(m);
                            Log.i("trying to send after ",Integer.toString(global_proposedNum));
                            Log.i("trying to send before ",(proposed.toString()));
                            proposed.add(global_proposedNum);
                            Log.i("trying to send after ",proposed.toString());
                        }catch(Exception e){

                            alive = 4 ;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Couldn't send message");
                    }
                }
                if(proposed.size() == alive){
                    if (proposed.size() > 0) {
                        agreed_sequence = find_maximum( proposed );
                    }
                    for(int i= 0 ; i < REMOTE_PORTS.length; i++){
                        Socket socket_i = socket_map.get( i );
                        PrintWriter out1 = new PrintWriter(socket_i.getOutputStream(), true);
                        out1.println(agreed_sequence);
                    }
                    proposed.clear();
                    socket_map.clear();
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
