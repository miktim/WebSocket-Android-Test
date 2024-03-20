package org.miktim.websockettest;

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

public class WsClientServerStressTest extends Thread {
    final MainActivity context;
    final ContextUtil util;
    static final int MAX_CLIENT_CONNECTIONS = 3; // allowed by server
    final int TEST_SHUTDOWN_TIMEOUT = 10000; //milliseconds
    final int PORT = 8080;
    final String ADDRESS = "ws://localhost:" + PORT;

    WebSocket webSocket = null;

    WsClientServerStressTest(MainActivity context) {
        this.context = context;
        util = new ContextUtil(context);
    }

    void ws_log(String msg) {
        util.sendBroadcastMessage(msg);
    }

    void testResult(WsConnection conn, int expected) {
        ws_log (conn.getStatus().code == expected ?  "OK" : "Failed!");
    }

    void joinAll(WebSocket ws) throws Exception {
        WsServer server = ws.listServers()[0];
        for(WsConnection conn : ws.listConnections()) conn.join();
        for(WsConnection conn : server.listConnections()) conn.join();
    }

    public void run() {
        ws_log(null); // clear console

        WsConnection.EventHandler handler = new WsConnection.EventHandler() {

            @Override
            public void onOpen(WsConnection conn, String subp) {
                try {
                    if(subp == null) {
                        conn.close(WsStatus.POLICY_VIOLATION,"Subprotocol required");
                        return;
                    }
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
// Server allowed connections
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
// check server allowed connections
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

        WsServer.EventHandler serverHandler = new WsServer.EventHandler() {
            @Override
            public void onStart(WsServer server) {
                ws_log("Server onStart: started");
            }

            @Override
            public boolean onAccept(WsServer server, WsConnection conn) {
                if (server.listConnections().length < MAX_CLIENT_CONNECTIONS)
                    return true;
                ws_log("Server onAccept: connection rejected.");
                return false;
            }

            @Override
            public void onStop(WsServer server, Exception e) {
                if(server.isInterrupted())
                    ws_log("Server onStop: interrupted" + (e != null ? " Error " + e : ""));
                else ws_log("Server closed");
            }
        };

        try {
            final WebSocket webSocket = new WebSocket();
            final WsParameters wsp = new WsParameters() // client/server parameters
                    .setHandshakeSoTimeout(5000)
                    .setSubProtocols("0,1,2,3,4,5,6,7,8,9".split(","));
                    //               .setMaxMessageLength(2000)
//                    .setPayloadBufferLength(0);// min payload length

            final Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    ws_log("Time is over!\n");
                    webSocket.listServers()[0].close("Time is over!");
                    timer.cancel();
                }
            }, TEST_SHUTDOWN_TIMEOUT);

            ws_log("\r\nWs client-server stress test "
                    + "\r\nClient try to connect to " + ADDRESS
                    + "\r\nConnections allowed by server: " + MAX_CLIENT_CONNECTIONS
                    + "\r\nTest will be terminated after "
                    + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                    + "\r\n");

            final WsServer wsServer = webSocket.Server(PORT, handler, wsp)
                    .setHandler(serverHandler).launch();

            ws_log("0. Connecting via TLS to a cleartext server (1002 expected):");
            WsConnection conn = webSocket.connect("wss://localhost:" + PORT, handler, wsp);
            joinAll(webSocket);
            testResult(conn, 1002);

            ws_log("\r\n0. Unsupported WebSocket subProtocol (1008):");
            wsp.setSubProtocols(new String[]{"10"});
            conn = webSocket.connect(ADDRESS, handler, wsp);
            joinAll(webSocket);
            testResult(conn, 1008);

            ws_log("\r\n1. Message too big (1009):");
            wsp.setSubProtocols(new String[]{"1"});
            conn = webSocket.connect(ADDRESS, handler, wsp);
            joinAll(webSocket);
            testResult(conn, 1009);

            ws_log("\r\n2. Message queue overflow (1008):");
            wsp.setSubProtocols(new String[]{"2"})
                    .setPayloadBufferLength(wsp.getMaxMessageLength()); // min
            conn = webSocket.connect(ADDRESS, handler, wsp);
            joinAll(webSocket);
            testResult(conn, 1008);

            ws_log("\r\n3. Trying connection timeout (1001):");
            wsp.setSubProtocols(new String[]{"3"})
                    .setConnectionSoTimeout(400, false);
            conn = webSocket.connect(ADDRESS, handler, wsp);
            joinAll(webSocket);
            testResult(conn, 1001);

            ws_log("\r\n4. Check server allowed connections ("
            + MAX_CLIENT_CONNECTIONS + "):");
            wsp.setSubProtocols(new String[]{"4"});
            for (int i = 0; i < MAX_CLIENT_CONNECTIONS + 2; i++) {
                webSocket.connect(ADDRESS, handler, wsp);
            }
            sleep(50); // waiting for rejected connections to be closed
            ws_log("[4] active connections: "
                    + wsServer.listConnections().length);

            ws_log("\r\n5. Trying to interrupt Server:");
            wsServer.interrupt();
            wsServer.join();

            ws_log("\r\n6. Attempt to connect to interrupted server (Exception):");
            try {
                webSocket.connect(ADDRESS, handler, wsp);
            } catch (IOException e) {
                ws_log("" + e +"\r\n");
            }

            joinAll(webSocket);
            ws_log("\r\nTest completed.");

        } catch (Exception e) {
            ws_log("Unexpected: " + e + "\r\n");
            e.printStackTrace();
        }
    }
}
