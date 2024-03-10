package org.miktim.websockettest;

import static android.content.Intent.ACTION_VIEW;

import android.content.Intent;
import android.net.Uri;

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsStatus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

public class WsServerTest extends Thread {
    final MainActivity context;
    final ContextUtil util;
    public static final int MAX_MESSAGE_LENGTH = 10000000;// bytes
    public static final int TEST_SHUTDOWN_TIMEOUT = 20000;// millis
    public static final String WEBSOCKET_SUBPROTOCOLS = "chat,superChat";

    String[] testNames = new String[]{
            "0. unknown WebSocket subprotocol (1006 expected)",
            "1. closing WebSocket by browser (1000 expected)",
            "2. closing WebSocket by server (1000 expected)",
            "3. waiting message too big (1009 expected)",
            "4. ping, waiting for server shutdown (1001 expected)"};

    WsServerTest(MainActivity activity) {
        context = activity;
        util = new ContextUtil(context);
    }

    void ws_log(String msg) {
        util.sendBroadcastMessage(msg);
    }

    String getTestId(WsConnection con) {
        String path = con.getPath();
        return path == null ? "E" : path.substring(path.length() - 1);
    }

    public void run() {
        try {
            final WebSocket webSocket;
            webSocket = new WebSocket(InetAddress.getByName("localhost"));
            WsParameters wsp = (new WsParameters())
                    .setMaxMessageLength(MAX_MESSAGE_LENGTH)
                    .setConnectionSoTimeout(1000, true) // ping on 1 second timeout
                    .setSubProtocols(WEBSOCKET_SUBPROTOCOLS.split(","));
            final WsServer server = webSocket.Server(8080, handler, wsp);
            server.start();
// init shutdown timer
            final Timer timer = new Timer(true); // is daemon
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    server.close("Time is over!");
                    timer.cancel();
                    ws_log("Time is over!");
                }
            }, TEST_SHUTDOWN_TIMEOUT);

            ws_log("\r\nWS server test "
                    + "\r\nIncoming maxMessageLength: " + MAX_MESSAGE_LENGTH
                    + "\r\nWebSocket subProtocols: " + WEBSOCKET_SUBPROTOCOLS
                    + "\r\nTest will be terminated after "
                    + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                    + "\r\nView WsServerTest.html in the default browser"
                    + "\r\n");
// call the default browser
            String testUrl = "http://miktim.github.io/websockettest/WsServerTest.html";
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(testUrl));
            context.startActivity(browserIntent);

        } catch (Exception e) {
            ws_log("Unexpected: " + e);
        }
    }

    WsConnection.EventHandler handler = new WsConnection.EventHandler() {
        @Override
        public void onOpen(WsConnection con, String subp) {
            String testId = getTestId(con);
            ws_log(testNames[Integer.parseInt(testId)]);
            String hello = "Hello, Browser! Привет, Браузер! ";
            ws_log(String.format("[%s] server side onOPEN: %s%s %s",
                    testId,
                    con.getPath(),
                    (con.getQuery() == null ? "" : "?" + con.getQuery()) //                + " Peer: " + con.getPeerHost()
                    ,
                    " Subprotocol: " + subp));
            try {
                con.send(hello);
            } catch (IOException e) {
                ws_log(String.format("[%s] server side onOPEN send error: %s",
                        testId,
                        e));
//            e.printStackTrace();
            }
        }

        @Override
        public void onClose(WsConnection con, WsStatus status) {
            String testId = getTestId(con);
            ws_log(String.format("[%s] server side onCLOSE: %s %s\r\n",
                    testId,
                    con.getPath(),
                    status));
        }

        @Override
        public void onError(WsConnection con, Throwable e) {
            String testId = getTestId(con);
            ws_log(String.format("[%s] server side onERROR: %s",
                    testId,
                    e));
//                e.printStackTrace();
        }

        @Override
        public void onMessage(WsConnection con, InputStream is, boolean isText) {
            String testId = getTestId(con);
            int messageLen;
            byte[] messageBuffer = new byte[MAX_MESSAGE_LENGTH];
            String message;
            try {
                messageLen = is.read(messageBuffer, 0, messageBuffer.length);
                if (is.read() != -1) {
                    throw new IOException("Message too big");
                } else if (isText) {
                    message = new String(messageBuffer, 0, messageLen, "UTF-8");
                    if (testId.equals("1")) { // wait browser closure
//                    ws_log(server + " onTEXT: ");
                        if (con.isOpen()) {
                            con.send(message);
                        }
                    } else if (testId.equals("2")) { // close by server
                        if (message.length() > 128) {
                            con.close(WsStatus.NORMAL_CLOSURE,
                                    "Closed by server. Trim close reason longer than 123 bytes: lo-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-ng reason lo-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-ng reason");
                        } else {
                            if (con.isOpen()) {
                                con.send(message + message);
                            }
                        }
                    } else if (testId.equals("3")) { // message too big
                        if (con.isOpen()) {
                            con.send(message + message); // can throw java.lang.OutOfMemoryError
                        }
                    } else if (testId.equals("4")) { // ping, wait server shutdown
                    } else {
                        con.send(message);
                    }
                } else {
                    ws_log("Unexpected binary: ignored");
                }
            } catch (IOException e) {
                ws_log(String.format("[%s] server side onMessage send error: %s",
                        testId, e));
            } catch (Error e) {
                ws_log(String.format("[%s] Fatal error: %s",
                        testId, e));
            }
        }
    };

}


