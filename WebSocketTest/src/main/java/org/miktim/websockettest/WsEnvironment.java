package org.miktim.websockettest;

import android.os.Build;

import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

public class WsEnvironment {
    final MainActivity context;
    final ContextUtil util;

    public WsEnvironment(MainActivity context) {
        this.context = context;
        util = new ContextUtil(context);
    }

    void ws_log(String msg) {
        util.sendBroadcastMessage(msg);
    }

    public static String join(Object[] array, String delimiter) {
        if (array == null || array.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object obj : array) {
            sb.append(obj).append(delimiter);
        }
        return sb.delete(sb.length() - delimiter.length(), sb.length()).toString();
    }

    public void start() {

        try {
            ws_log(null); // clear console
            ws_log("Execution environment.");
            ws_log("\nSystem properties:");
            String[] sa = new String[]{"os.name", "os.version", "java.vendor", "java.version"};
            for (String s : sa) {
                ws_log(s + ": " + System.getProperty(s));
            }
            ws_log("\nAndroid API: " + Build.VERSION.SDK_INT);

            SSLContext sslContext = null;
            sslContext = SSLContext.getDefault();
            SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
            ws_log("\r\nDefault SSLParameters.\r\nProtocols:");
            sa = sslParameters.getProtocols();
            ws_log("\"" + join(sa, "\", \"") + "\"");
            sa = sslParameters.getCipherSuites();
            ws_log("Chipher suites:");
            ws_log("\"" + join(sa, "\",\r\n\"") + "\"");

            ws_log("\r\nKeyManagerFactory default algorithm:\r\n\""
                    + KeyManagerFactory.getDefaultAlgorithm() + "\"");
            ws_log("KeyStore default type:\r\n\""
                    + KeyStore.getDefaultType() + "\"");
            ws_log("TrustManagerFactory default algorithm:\r\n\""
                    + TrustManagerFactory.getDefaultAlgorithm() + "\"");
            ws_log("\nCompleted");
        } catch (NoSuchAlgorithmException e) {
            ws_log("\nError: " + e);
            e.printStackTrace();
        }
    }

}
