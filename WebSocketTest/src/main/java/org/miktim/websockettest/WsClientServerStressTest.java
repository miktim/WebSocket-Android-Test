package org.miktim.websockettest;

import static java.lang.Thread.sleep;

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

public class WsClientServerStressTest {
    final MainActivity context;
    final ContextUtil util;
    final int TEST_SHUTDOWN_TIMEOUT = 10000; //milliseconds
    final int PORT = 8080;
    final String ADDRESS = "ws://localhost:" + PORT;

    WsClientServerStressTest(MainActivity context) {
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

    void start() {
        ws_log(null); // clear console

        WsConnection.EventHandler handler = new WsConnection.EventHandler() {

            @Override
            public void onOpen(WsConnection conn, String subp) {
                try {
                    if (subp.equals("1") && conn.isClientSide()) {
// message too big
                        conn.send(new byte[conn.getParameters()
                                .getMaxMessageLength() + 1]);
                    } else if (subp.equals("2") && conn.isClientSide()) {
// message queue overflow
                        for (int i = 0; i < 100; i++) {
                            conn.send(new byte[conn.getParameters().getMaxMessageLength()]);
                            conn.send("Blah Blah Blah Blah Blah Blah Blah Blah Blah Blah ");
                        }
                    } else if (subp.equals("3")) {
// there is nothing to do, wait for a timeout
                    } else if (subp.equals("4")) {
// Server interrupt
                        conn.send((conn.isClientSide()
                                ? "Hello, Server!" : "Hello, client!"));
                    }
                } catch (IOException e) {
                    ws_log(String.format("[%s] %s send Error %s",
                            conn.getSubProtocol(),
                            (conn.isClientSide() ? "Client" : "Server side"),
                            e.toString()
                    ));
                }
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
                ws_log(String.format("[%s] %s onClose %s%s",
                        (conn.getSubProtocol() == null ? "0" : conn.getSubProtocol()),
                        (conn.isClientSide() ? "Client" : "Server side"),
                        status.toString(),
                        (status.error != null ? " Error " + status.error.toString() : "")
                ));
            }

            @Override
            public void onError(WsConnection conn, Throwable e) {
            }

            @Override
            public void onMessage(WsConnection conn, InputStream is, boolean isText) {
                String subp = conn.getSubProtocol();
                try {
                    if (subp.equals("2")) {
// message queue overflow
                        if (conn.isOpen()) {
                            conn.send(is, isText);
                        }
                    } else if (subp.equals("4")) {
// terminate server
                        if (conn.isOpen()) {
                            conn.send(is, isText);
                        }
                    }

                } catch (IOException e) {
                    ws_log(String.format("[%s] %s onMessage send error %s",
                            subp,
                            (conn.isClientSide() ? "Client" : "Server side"),
                            e.toString()));
                }

            }

        };

        try {
            final WebSocket webSocket = new WebSocket();
            final WsParameters wsp = new WsParameters() // client/server parameters
                    .setSubProtocols("0,1,2,3,4,5,6,7,8,9".split(","))
                    //               .setMaxMessageLength(2000)
                    .setPayloadBufferLength(0);// min payload length

            final WsServer wsServer = webSocket.Server(PORT, handler, wsp);
            wsServer.start();

            final Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    webSocket.closeAll("Time is over!");
                    timer.cancel();
//                    ws_log("\r\nCompleted.");
                }
            }, TEST_SHUTDOWN_TIMEOUT);

            ws_log("\r\nWs client-server stress test "
//                    + WebSocket.VERSION
                    + "\r\nClient try to connect to " + ADDRESS
                    + "\r\nTest will be terminated after "
                    + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                    + "\r\n");

            ws_log("0. Try connecting via TLS to a cleartext server:");
            WsConnection conn = webSocket.connect("wss://localhost:" + PORT, handler, wsp);
            conn.join();

            ws_log("\r\n0. Try unsupported WebSocket subProtocol");
            wsp.setSubProtocols(new String[]{"10"});
            conn = webSocket.connect(ADDRESS, handler, wsp);
            conn.join();

            ws_log("\r\n1. Try message too big");
            wsp.setSubProtocols(new String[]{"1"});
            conn = webSocket.connect(ADDRESS, handler, wsp);
            conn.join();

            ws_log("\r\n2. Try message queue overflow:");
            wsp.setSubProtocols(new String[]{"2"})
                    .setPayloadBufferLength(wsp.getMaxMessageLength()); // min
            conn = webSocket.connect(ADDRESS, handler, wsp);
            conn.join();

            ws_log("\r\n3. Try connection timeout:");
            wsp.setSubProtocols(new String[]{"3"})
                    .setConnectionSoTimeout(400, false);
            conn = webSocket.connect(ADDRESS, handler, wsp);
            conn.join();

            ws_log("\r\n4. Try interrupt Server:");
            wsp.setSubProtocols(new String[]{"4"});
            webSocket.connect(ADDRESS, handler, wsp);
            webSocket.connect(ADDRESS, handler, wsp);
            webSocket.connect(ADDRESS, handler, wsp);
            webSocket.connect(ADDRESS, handler, wsp);
            sleep(500);
            wsServer.interrupt();
        } catch (Exception e) {
            ws_log("Unexpected: " + e);
        }
    }
}