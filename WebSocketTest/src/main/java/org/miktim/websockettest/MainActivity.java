/*
 * WebSocket test. MIT (c) 2023 miktim@mail.ru
 */

package org.miktim.websockettest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import org.miktim.websocket.WebSocket;

public class MainActivity extends AppCompatActivity {
// keyFileName;password
    static String KEY_FILE = "localhost.bks;qwerty"; // BKS type
//    static String KEY_FILE = "localhost.pkcs;qwerty"; // PKCS #12

    WsConsole console;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        console = new WsConsole();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(BROADCAST_MESSAGE));
        getSupportActionBar().setTitle("WebSocket " + WebSocket.VERSION + " test");
    }

    @Override
    public void finish() {
        super.finish();
        System.exit(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        console.erase();
        int itemId = item.getItemId();
        if (itemId == R.id.WSClientServerTest) {
            (new WsWssClientServerTest(this, "ws")).start();
            return true;
        } else if (itemId == R.id.WSSClientServerTest) {
            (new WsWssClientServerTest(this, "wss")).start();
            return true;
        } else if (itemId == R.id.WSSClientTest) {
            (new WssClientTest(this)).start();
            return true;
//        } else if (itemId == R.id.WSServerTest) {
//            (new WsServerTest(this)).start();
//            return true;
        } else if (itemId == R.id.ClientServerStressTest){
            (new WsClientServerStressTest(this)).start();
            return true;
        } else if (itemId == R.id.Exit) {
            finish();
            return true;
        }
        return false;
    }

    static String BROADCAST_MESSAGE = "org.miktim.MSG";
    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            String message = intent.getStringExtra("message");
            if (message == null) console.erase();
            else console.println(message);
        }
    };

    public class WsConsole {
        TextView consoleTextView = findViewById(R.id.ConsoleTextView);

        public WsConsole() {
            erase();
        }

        public void println(String msg) {
            Log.d("", msg);
            consoleTextView.append(msg + "\n\r");
         }
        public void erase() {
            consoleTextView.setText("");
        }
    }
}

