/*
 * WebSocket client-server test. (c) websocketstest.com
 * Adapted by miktim@mail.ru, march 2021
 */

package org.miktim.websockettest;

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class WsWssClientServerTest {

    final MainActivity context;
    final ContextUtil util;
    final int MAX_MESSAGE_LENGTH = 10000; //
    final int TEST_SHUTDOWN_TIMEOUT = 7000; //milliseconds
    int PORT = 8080;
    String REMOTE_CONNECTION;//
    String scheme = "ws";

    String fragmentTest = randomString(512);
    int counter = 0;

    WsWssClientServerTest(MainActivity activity, String scheme) {
        context = activity;
        util = new ContextUtil(context);
        this.scheme = scheme;
        PORT = scheme.equals("ws") ? 8080 : 8443;
        REMOTE_CONNECTION = scheme + "://localhost:" + PORT;
    }

    void ws_log(String msg) {
        util.sendBroadcastMessage(msg);
    }

    public void ws_send(WsConnection con, String msg) throws IOException {
        ws_log("snd: " + msg);
        con.send(msg);
    }

    String randomString(int string_length) {
        String chars = "0123456789АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЬЪЭЮЯABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
        StringBuilder randomstring = new StringBuilder();
        for (int i = 0; i < string_length; i++) {
            int rnum = (int) Math.floor(Math.random() * chars.length());
            randomstring.append(chars.charAt(rnum));
        }
        return randomstring.toString();
    }

    synchronized void sleep(int millis) throws InterruptedException {
        this.wait(millis);
    }

    WsConnection.EventHandler serverSideHandler = new WsConnection.EventHandler(){
        String cmd = "";

        @Override
        public void onOpen(WsConnection conn, String subProtocol) {
            try {
                conn.send("connected,");
                ws_log("Server side opened.");
            } catch (IOException e) {
                ws_log("Server side onOpen send() error: " + e);
                e.printStackTrace();
            }
        }

        @Override
        public void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text) {
            byte[] messageBuffer = new byte[MAX_MESSAGE_LENGTH];
            int messageLen;
            try {
                messageLen = is.read(messageBuffer);
                if (is.read() != -1) {
                    conn.close(WsStatus.MESSAGE_TOO_BIG, "Message too big");
                    ws_log("Server side: message too big");
                    return;
                }
                if (isUTF8Text) {
                    cmd = new String(messageBuffer, 0, messageLen, "UTF-8");
                } else {
                    ws_log("Server side: unexpected binary. Ignored");
                    return;
                }

                switch (cmd.split(",")[0]) {
                    case ("version"):
                        conn.send("version,hybi-draft-13");
                        break;
                    case ("echo"):
                        conn.send(cmd);
                        break;
                    case ("ping"):
                        sleep(1000);
                        if(conn.isOpen()) conn.send("ping,success");
                        break;
                    case ("fragments"):
                        conn.send(cmd);
                        break;
                    case ("timer"):
                        SimpleDateFormat formatter
                                = new SimpleDateFormat ("yyyy/MM/dd hh:mm:ss");
                        while (conn.isOpen()) {
                            conn.send("time,"+ formatter.format(new Date()));
                            sleep(1000);
                        }
                        break;
                    default:
                        ws_log("Server side: unknown command. Ignored. ");
                }
            } catch (Exception e) {
                ws_log("Server side onMessage error: " + e);
                e.printStackTrace();
            }
        }

        @Override
        public void onError(WsConnection conn, Throwable e) {
            ws_log("Server side onError: " + e);
        }

        @Override
        public void onClose(WsConnection conn, WsStatus closeStatus) {
            ws_log("Server side closed. "+ closeStatus);
        }
    };

    WsConnection.EventHandler clientHandler = new WsConnection.EventHandler() {
        @Override
        public void onOpen(WsConnection con, String subp) {
            String protocol = con.getSSLSessionProtocol();
            ws_log("Client connected: " +
                    (protocol == null ? "clear text" : protocol));
//            WsParameters wsp = con.getParameters(); // debug
        }

        @Override
        public void onClose(WsConnection con, WsStatus status) {
            ws_log("Client closed. " + status);
        }

        @Override
        public void onError(WsConnection con, Throwable e) {
            ws_log("Client onError: " + e.toString() + " " + con.getStatus());
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
                ws_log("Client onMesage error: " + e);
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
                    ws_log("Unknown command.");
                }
            } catch (IOException e) {
                ws_log("snd error: " + e.toString());
            }
        }

        public void onMessage(WsConnection con, byte[] b) {
            ws_log("rcv: unexpected binary. Ignored.");
        }
    };

    void start() {
        ws_log(null); // clear console
        try {
            String[] keyInfo = MainActivity.KEY_FILE.split(";");
            util.saveAssetAsFile(keyInfo[0]);
            final WebSocket webSocket = new WebSocket(InetAddress.getByName("localhost"));
            if(scheme.equals("wss"))
                webSocket.setKeyFile(util.keyFile(keyInfo[0]), keyInfo[1]);
            WsParameters wsp = new WsParameters() //
                    .setConnectionSoTimeout(200, true)
                    .setPayloadBufferLength(0); // min buffer
//            String sslProtocols = "TLSv1.2"; //"TLSv1.3 TLSv1.2 TLSv1.1 TLSv1"
//            wsp.getSSLParameters().setProtocols(sslProtocols.split(" "));
//            wsp.setSSLParameters(null);
            ws_log("\r\nWs/Wss client-server test"
                    + "\r\nTrying to connect to " + REMOTE_CONNECTION
                    + "\r\nTest will be terminated after "
                    + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                    + "\r\n");
            WsServer wsServer;


            if (scheme.equals("wss")) {
                wsServer
                        = webSocket.SecureServer(PORT, serverSideHandler, wsp).launch();
            } else {
                wsServer
                        = webSocket.Server(PORT, serverSideHandler, wsp).launch();
            }
            final WsConnection wsConnection
                        = webSocket.connect(REMOTE_CONNECTION, clientHandler, wsp);


            final Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    webSocket.closeAll("Time is over!");
                    timer.cancel();
//                    ws_log("\r\nCompleted.");
                }
            }, TEST_SHUTDOWN_TIMEOUT);
        } catch (Throwable e) {
            ws_log("Unexpected: " + e);
            e.printStackTrace();
        }
    }
}
