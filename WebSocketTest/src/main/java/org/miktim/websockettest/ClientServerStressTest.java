package org.miktim.websockettest;

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsHandler;
import org.miktim.websocket.WsListener;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

public class ClientServerStressTest {
    final MainActivity context;
    final ContextUtil util;
    final int TEST_SHUTDOWN_TIMEOUT = 10000; //milliseconds
    final int BACKLOG = 2;

    ClientServerStressTest(MainActivity context) {
        this.context = context;
        util = new ContextUtil(context);
    }

    void ws_log(String msg) {
        util.sendBroadcastMessage(msg);
    }

    public void ws_send(WsConnection con, String msg) throws IOException {
        ws_log("snd: " + msg);
        con.send(msg);
    }

    WebSocket webSocket = null;
    WsListener pListener = null;
    WsListener sListener = null;

    WsHandler listenerHandler = new WsHandler() {
        @Override
        public void onOpen(WsConnection conn, String subProtocol) {
            int testId = Integer.parseInt(subProtocol == null ? "0" : subProtocol);
        }

        @Override
        public void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text) {
            String subProtocol = conn.getSubProtocol();
            int testId = Integer.parseInt(subProtocol == null ? "0" : subProtocol);
            try {
                conn.send(is, isUTF8Text);
            } catch (IOException e) {
                ws_log("Test" + testId + "Listener send error: " + e);
            }
        }

        @Override
        public void onError(WsConnection conn, Throwable e) {
            String subProtocol = conn.getSubProtocol();
            int testId = Integer.parseInt(subProtocol == null ? "0" : subProtocol);
            if(conn == null) ws_log("Listener interrupted: " + e);
            else ws_log("Test" + testId + " Listener onError: " + e);
        }

        @Override
        public void onClose(WsConnection conn, WsStatus closeStatus) {
            String subProtocol = conn.getSubProtocol();
            int testId = Integer.parseInt(subProtocol == null ? "0" : subProtocol);
            ws_log("Test" + testId + " " + closeStatus);

        }
    };

    WsHandler clientHandler = new WsHandler() {
        @Override
        public void onOpen(WsConnection conn, String subProtocol) {
            try {
                conn.send("Blah Blah");
            } catch (IOException e) {

            }
        }

        @Override
        public void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text) {
            String subProtocol = conn.getSubProtocol();
            int testId = Integer.parseInt(subProtocol == null ? "0" : subProtocol);
            try {
                conn.send(is, isUTF8Text);
            } catch (IOException e) {
            }
        }

        @Override
        public void onError(WsConnection conn, Throwable e) {
        }

        @Override
        public void onClose(WsConnection conn, WsStatus closeStatus) {
            String subProtocol = conn.getSubProtocol();
            int testId = Integer.parseInt(subProtocol == null ? "0" : subProtocol);
            ws_log("Test" + testId + " " + closeStatus);
        }
    };

    void start() {
        ws_log(null); // clear console
        ws_log("\r\nClient-server stress test"
//                + "\r\nBacklog " + BACKLOG
                + "\r\nTest will be terminated after "
                + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");

        try {
            webSocket = new WebSocket(InetAddress.getByName("localhost"));
            String[] keyInfo = MainActivity.KEY_FILE.split(";");
            util.saveAssetAsFile(keyInfo[0]);
            webSocket.setKeyFile(util.keyFile(keyInfo[0]), keyInfo[1]);
            WsParameters wParam = (new WsParameters())
                    .setSubProtocols("0 1 2 3 4 5 6 7 8 9".split(" "))
                    .setBacklog(BACKLOG)
                    .setMaxMessageLength(0);
            pListener = webSocket.listen(8080, listenerHandler, wParam);
            sListener = webSocket.listenSafely(8443, listenerHandler, wParam);

// Try plain connection to TLS listener
            ws_log("Try plain connection to TLS listener");
            wParam.setSubProtocols(new String[]{"0"});
            WsConnection conn = webSocket.connect("ws://localhost:8443", clientHandler, wParam);
            if(conn.getStatus().error instanceof java.net.SocketException)
                ws_log("OK "+conn.getStatus().error);
            else ws_log("Failed!");


// Try TLS connection without trusted key
            webSocket.resetKeyFile();
            ws_log("Try TLS connection without trusted key");
            wParam.setSubProtocols(new String[]{"1"});
            conn = webSocket.connect("wss://localhost:8443", clientHandler, wParam);
            if(conn.getStatus().error instanceof javax.net.ssl.SSLHandshakeException)
                ws_log("OK " + conn.getStatus().error);
            else ws_log("Failed!");

// Try MESSAGE_TOO_BIG
            wParam.setSubProtocols(new String[]{"2"});
            conn = webSocket
                    .connect("ws://localhost:8080", clientHandler, wParam);

// Test interrupt, closing client conns
            wParam.setSubProtocols(new String[]{"3"});
            for (int i = 0; i < 3; i++) {
                conn = webSocket.connect("ws://localhost:8080",clientHandler,wParam);
            }
            pListener.interrupt();
            for(WsConnection con : webSocket.listConnections()){
                con.close();
            }

            final Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    ws_log("\n\rCompleted\n\r");
                    webSocket.closeAll("Time is over!");
                    timer.cancel();
                }
            }, TEST_SHUTDOWN_TIMEOUT);

        } catch (Throwable e) {
            webSocket.closeAll("");
            e.printStackTrace();
            ws_log("Unexpected " + e);
        }
    }
}
