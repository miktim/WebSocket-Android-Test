package org.miktim.websockettest;

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsHandler;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class WssClientTest {
    final MainActivity context;
    final ContextUtil util;
    final int MAX_MESSAGE_LENGTH = 10000; //
    final int TEST_SHUTDOWN_TIMEOUT = 15000; //milliseconds
    final String REMOTE_CONNECTION = "wss://websocketstest.com:443/service";//

    String fragmentTest = randomString(512);
    int counter = 0;

    WssClientTest(MainActivity activity) {
        context = activity;
        util = new ContextUtil(context);
    }

    void ws_log(String msg) {
        util.sendBroadcastMessage(msg);
   }

    public void ws_send(WsConnection con, String msg) throws IOException {
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

    WsHandler clientHandler = new WsHandler() {
        @Override
        public void onOpen(WsConnection con, String subp) {
            ws_log("Connected. " + con.getSSLSessionProtocol());
            WsParameters wsp = con.getParameters(); // debug
        }

        @Override
        public void onClose(WsConnection con, WsStatus status) {
            ws_log("Connection closed. " + status);
        }

        @Override
        public void onError(WsConnection con, Throwable e) {
            ws_log("Error: " + e.toString() + " " + con.getStatus());
//                e.printStackTrace();
        }

        @Override
        public void onMessage(WsConnection con, InputStream is, boolean isText) {
            byte[] messageBuffer = new byte[MAX_MESSAGE_LENGTH];
            int messageLen = 0;

            try {
                messageLen = is.read(messageBuffer, 0, messageBuffer.length);
                if (is.read() != -1) {
                    con.close(WsStatus.MESSAGE_TOO_BIG, "Message too big");
                } else if (isText) {
                    onMessage(con, new String(messageBuffer, 0, messageLen, "UTF-8"));
                } else {
                    onMessage(con, Arrays.copyOfRange(messageBuffer, 0, messageLen));
                }
            } catch (Exception e) {
                ws_log("Client read error: " + e);
//                    e.printStackTrace();
            }
        }

        public void onMessage(WsConnection con, String s) {
            try {
                String packet = s;
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
                    }
                } else {
                    ws_log("rcv Unknown command.");
                }
            } catch (IOException e) {
                ws_log("snd error: " + e.toString());
            }
        }

        public void onMessage(WsConnection con, byte[] b) {
            ws_log("rcv: unexpected binary");
        }
    };

    void start() {
        ws_log(null); // clear console
        try {
//            WebSocket.setKeyStore("/nonexistentfile", ""); // reset
            WebSocket.setTrustStore("/nonexistentfile", "");
            final WebSocket webSocket = new WebSocket();
            WsParameters wsp = new WsParameters();
            wsp.setConnectionSoTimeout(4000, true); // ping
//        wsp.setPayloadLength(fragmentTest.length()/2); // not work!
            ws_log("\r\nWssClient test"
                    + "\r\nTrying to connect to " + REMOTE_CONNECTION
                    + "\r\nTest will be terminated after "
                    + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                    + "\r\n");
            final WsConnection wsConnection
                    = webSocket.connect(REMOTE_CONNECTION, clientHandler, wsp);
            final Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    wsConnection.close(WsStatus.GOING_AWAY, "Time is over!");
                    timer.cancel();
                }
            }, TEST_SHUTDOWN_TIMEOUT);
        } catch (Throwable e) {
            ws_log("Unexpected: " + e);
            e.printStackTrace();
        }
    }
}