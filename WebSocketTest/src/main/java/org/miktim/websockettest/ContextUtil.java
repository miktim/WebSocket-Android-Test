package org.miktim.websockettest;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ContextUtil {
    MainActivity context;

    ContextUtil(MainActivity activity) {
        context = activity;
    }

    void sendBroadcastMessage(String msg) {
        Intent intent = new Intent();
        intent.putExtra("message", msg);
        intent.setAction(MainActivity.BROADCAST_MESSAGE);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
    File keyFile(String asset) {
        return new File(context.getFilesDir(), asset);
    }
    boolean saveAssetAsFile(String asset) {
        try (InputStream is = context.getAssets().open(asset);
             OutputStream os = new FileOutputStream(keyFile(asset))) {
            byte[] buffer = new byte[512];
            int i;
            while ((i = is.read(buffer)) > -1) {
                os.write(buffer, 0, i);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
