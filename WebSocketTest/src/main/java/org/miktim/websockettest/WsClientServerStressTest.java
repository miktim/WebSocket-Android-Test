package org.miktim.websockettest;

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsError;
import org.miktim.websocket.WsMessage;
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
    final int TEST_SHUTDOWN_TIMEOUT = 30000; //milliseconds
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
        for(WsServer server : ws.listServers()) {
            for (WsConnection conn : server.listConnections()) conn.join();
        }
        for(WsConnection conn : ws.listConnections()) conn.join();
    }

    public void run() {
        ws_log(null); // clear console

            WsServer.Handler handler = new WsServer.Handler() {

            @Override
            public void onOpen(WsConnection conn, String subp) {
                    if(subp == null) {
                        conn.close("SubProtocol: null");
                        return;
                    }
                    if (subp.equals("1") && conn.isClientSide()) {
// message too big
                        conn.send(new byte[conn.getParameters()
                                .getMaxMessageLength() + 1]);
                    } else if (subp.equals("2") && conn.isClientSide()) {
// message queue overflow
                        for (int i = 0; i < 100 && conn.isOpen(); i++) {
                            conn.send("Blah Blah Blah Blah Blah Blah Blah Blah Blah Blah");
                        }
                    } else if (subp.equals("3")) {
// there is nothing to do, wait for a timeout
                    } else if (subp.equals("4")) {
// Server allowed connections
                        if(conn.isOpen())
                            conn.send((conn.isClientSide()
                                ? "Hello, Server!" : "Hello, client!"));
                    } else if (subp.equals("5") && !conn.isClientSide()) {
// RuntimeException in the handler
                        throw new NullPointerException("onOpen");
                    }
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
                ws_log(String.format("[%s] %s onClose %s",
                        (conn.getSubProtocol() == null ? "0" : conn.getSubProtocol()),
                        (conn.isClientSide() ? "Client" : "Server side"),
                        status.toString()
                ));
            }

            @Override
            public void onError(WsConnection conn, Throwable e) {
                ws_log(String.format("[%s] %s onError: %s",
                        conn.getSubProtocol(),
                        (conn.isClientSide() ? "Client" : "Server side"),
                        e.toString()));
            }
            @Override
            public void onMessage(WsConnection conn, WsMessage msg) {
                String subp = conn.getSubProtocol();
                try {
                    if (subp == null) {
// ignore
                    } else if (subp.equals("2")) {
// message queue overflow
                        sleep(200);
                    } else if (subp.equals("4")) {
// check server allowed connections
                        if (conn.isOpen()) {
                            conn.send(msg, msg.isText());
                        }
                    }
                } catch (InterruptedException ignore) {
                } catch (IOException e) {
                    ws_log(String.format("[%s] %s onMessage send error %s",
                            subp,
                            (conn.isClientSide() ? "Client" : "Server side"),
                            e.toString()));
                }

            }

            @Override
            public void onStart(WsServer server, WsParameters wsp) {
                ws_log("Server started");
            }

            @Override
            public void onStop(WsServer server, Throwable e) {
                 ws_log((e != null ? "Server abnormal shutdown " + e : "Server shutdown"));
            }
        };

        try {
            final WebSocket webSocket = new WebSocket();
            final WsParameters wsp = new WsParameters() // client/server parameters
                    .setHandshakeSoTimeout(5000)
                    .setSubProtocols("0,1,2,3,4,5,6,7,8,9".split(","))
                    .setBacklog(MAX_CLIENT_CONNECTIONS);

            final Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    ws_log("\nTime is over!\n");
                    webSocket.closeAll("Time is over!");
                    timer.cancel();
                }
            }, TEST_SHUTDOWN_TIMEOUT);

            ws_log("\r\nWs client-server stress test "
                    + "\r\nClient try to connect to " + ADDRESS
                    + "\r\nConnections allowed by server: " + MAX_CLIENT_CONNECTIONS
                    + "\r\nTest will be terminated after "
                    + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                    + "\r\n");

            final WsServer wsServer = webSocket.startServer(PORT, handler, wsp);

            ws_log("0. Connecting via TLS to a cleartext server (1002 expected):");
            WsConnection conn = webSocket.connect("wss://localhost:" + PORT, handler, wsp);
            joinAll(webSocket);
            testResult(conn, 1002);

            ws_log("\r\n0. Unsupported WebSocket subProtocol (1000):");
            wsp.setSubProtocols(new String[]{"10"});
            conn = webSocket.connect(ADDRESS, handler, wsp);
            joinAll(webSocket);
            testResult(conn, 1000);

            ws_log("\r\n1. Message too big (1009):");
            wsp.setSubProtocols(new String[]{"1"});
            conn = webSocket.connect(ADDRESS, handler, wsp);
            joinAll(webSocket);
            testResult(conn, 1009);

            ws_log("\r\n2. Message queue overflow (1008):");
            wsp.setSubProtocols(new String[]{"2"})
                    .setPayloadBufferLength(wsp.getMaxMessageLength());
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
            wsp.setSubProtocols(new String[]{"4"}).setBacklog(MAX_CLIENT_CONNECTIONS)
                    .setConnectionSoTimeout(500, true); // restore ping enabled
            for (int i = 0; i < MAX_CLIENT_CONNECTIONS + 2; i++) {
                webSocket.connect(ADDRESS, handler, wsp);
            }
            sleep(50); // waiting for rejected connections to be closed
            int connCount = wsServer.listConnections().length;
            ws_log("\n[4] active connections: "
                    + connCount + (connCount == MAX_CLIENT_CONNECTIONS ? " OK\n" : "Failed!\n"));
            for(WsConnection con : webSocket.listConnections()) {
                con.close("[4] completed");
            }
            joinAll(webSocket);

            ws_log("\r\n5. Trying NullPointerException in the handler (1006): " );
            wsp.setSubProtocols(new String[] {"5"});
            conn = webSocket.connect(ADDRESS, handler, wsp);
            joinAll(webSocket);
            testResult(conn, 1006);

            ws_log("\r\n6. Attempt to crash server (1011):");
            wsp.setSubProtocols(new String[] {"6"});
            conn = webSocket.connect(ADDRESS, handler, wsp)
                    .ready();
            wsServer.getServerSocket().close();
            joinAll(webSocket);
            testResult(conn, 1011);
            ws_log("\r\nTest completed.");
            timer.cancel();

        } catch (Throwable e) {
            ws_log("Unexpected: " + e + "\r\n");
            e.printStackTrace();
        }
    }
}
