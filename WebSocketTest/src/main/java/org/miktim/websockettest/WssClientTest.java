/*
 * Secure WebSocket client test. (c) websocketstest.com
 * Adapted by @miktim, march 2021
 */

package org.miktim.websockettest;

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsError;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

import java.util.Timer;
import java.util.TimerTask;

public class WssClientTest {
    final MainActivity context;
    final ContextUtil util;
    final int MAX_MESSAGE_LENGTH = 10000; // bytes
    final int TEST_SHUTDOWN_TIMEOUT = 10000; // millis
    final String REMOTE_CONNECTION = "wss://websocketstest.com/service";//
    String fragmentTest = randomString(512);
    int counter = 0;
    WsConnection wsConnection;
    final Timer timer = new Timer();

    WssClientTest(MainActivity activity) {
        context = activity;
        util = new ContextUtil(context);
    }

    void ws_log(String msg) {
        util.sendBroadcastMessage(msg);
    }

    public void ws_send(WsConnection con, String msg) {
        ws_log("snd: " + msg);
        con.send(msg);
    }

    String randomString(int string_length) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
        StringBuilder randomstring = new StringBuilder();
        for (int i = 0; i < string_length; i++) {
            int rnum = (int) Math.floor(Math.random() * chars.length());
            randomstring.append(chars.charAt(rnum));
        }
        return randomstring.toString();
    }

    WsConnection.Handler clientHandler = new WsConnection.Handler() {
        @Override
        public void onOpen(WsConnection con, String subp) {
            ws_log("Connected. " + (con.getSSLSessionProtocol() == null ?
                    "cleartext" : con.getSSLSessionProtocol()));
//            WsParameters wsp = con.getParameters(); // debug
        }

        @Override
        public void onClose(WsConnection con, WsStatus status) {
            ws_log("Connection closed. " + status);
        }

        @Override
        public void onError(WsConnection con, Throwable e) {
            ws_log("Error: " + e.toString());
            e.printStackTrace();
        }

        @Override
        public void onMessage(WsConnection con, WsMessage msg) {
            if (!msg.isText()) {
                ws_log("rcv: unexpected binary");
                con.close(WsStatus.INVALID_DATA, "Unexpected binary");
                return;
            }

            String packet = msg.asString();
            String[] arr = packet.split(",", 2);
            String cmd = arr[0];
            String response = arr[1];
            ws_log("rcv: " + packet);
            if (cmd.equals("connected")) {
                ws_send(con, "version,");
            } else if (cmd.equals("version")) {
                if (!response.equals("hybi-draft-13")) {
                    ws_log("Something wrong...");
                } else ws_log("OK");
                counter = 0;
                ws_send(con, "echo,test message");
            } else if (cmd.equals("ping")) {
                if (!response.equals("success")) {
                    ws_log("Failed!");
                } else ws_log("OK");
                counter = 0;
                ws_send(con, "fragments," + fragmentTest);
            } else if (cmd.equals("fragments")) {
                if (!response.equals(fragmentTest)) {
                    ws_log("Failed!");
                } else ws_log("OK");
                counter = 0;
                ws_send(con, "timer,");
            } else if (cmd.equals("echo")) {
                if (!response.equals("test message")) {
                    ws_log("Failed!");
                } else ws_log("OK");
                ws_send(con, "ping,");
            } else if (cmd.equals("time")) {
                if (++counter > 4) {
                    ws_log("OK");
                    con.close(WsStatus.NORMAL_CLOSURE, "Completed");
                    timer.cancel();
                }
            } else {
                ws_log("rcv: unknown command");
            }
        }
    };

    void start() {
        ws_log(null); // clear console
        try {
            final WebSocket webSocket = new WebSocket();
            WsParameters wsp = new WsParameters()
                    .setMaxMessageLength(MAX_MESSAGE_LENGTH); //
//            wsp.setConnectionSoTimeout(2000, true);
//            String sslProtocols = "TLSv1.2";//TLSv1.2 TLSv1.1 TLSv1 TLSv1.3"; //
//            wsp.getSSLParameters().setProtocols(sslProtocols.split(" "));
//            wsp.getSSLParameters().setCipherSuites(
//                    new String[]{"TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA"});

//            wsp.setSSLParameters(null);

            if (android.os.Build.VERSION.SDK_INT < 25) {
                ws_log("WARNING! \nTLS connection requires API 25 and later." +
                        "\nCurrent API is " + android.os.Build.VERSION.SDK_INT);
            }

// the site does not accept fragmented messages
//          wsp.setPayloadLength(fragmentTest.length()/2); // not work!

            ws_log("\r\nWss client test"
                    + "\r\nTrying to connect to " + REMOTE_CONNECTION
                    + "\r\nTest will be terminated after "
                    + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                    + "\r\n");

            wsConnection
                    = webSocket.connect(REMOTE_CONNECTION, clientHandler, wsp);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    wsConnection.close(WsStatus.GOING_AWAY, "Time is over!");
                    timer.cancel();
                }
            }, TEST_SHUTDOWN_TIMEOUT);

            wsConnection.ready(); // waiting for handshake completed
            if (wsConnection.getStatus().error != null) {
                ws_log("\r\nTrying cleartext connection...\r\n");
                wsConnection
                        = webSocket.connect(REMOTE_CONNECTION.replace("wss:", "ws:"), clientHandler, wsp);
            }
            wsConnection.join();
        } catch (WsError | InterruptedException e ) {
            ws_log("Unexpected: " + e);
//            e.printStackTrace();
        }
        ws_log("\r\nTest completed");
    }
}