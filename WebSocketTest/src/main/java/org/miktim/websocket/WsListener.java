/*
 * WsListener. WebSocket listener, MIT (c) 2020-2023 miktim@mail.ru
 *
 * Accepts sockets, creates and starts connection threads.
 *
 * 3.3.1
 * - functions getPort(), getInetSocketAddress() added
 *
 * TODO separate exception handling from ServerSocket and clients
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WsListener extends Thread {

    private boolean isRunning;
    private final boolean isSecure;
    private final WsParameters wsp;
    private final ServerSocket serverSocket;
    private final WsHandler handler;
    List<Object> listeners = null;
    private final List<Object> connections = Collections.synchronizedList(new ArrayList<>());
    
    WsListener(ServerSocket ss, WsHandler h, boolean secure, WsParameters wsp) {
        this.serverSocket = ss;
        this.handler = h;
        this.isSecure = secure;
        this.wsp = wsp;
    }

    public boolean isSecure() {
        return isSecure;
    }

    public boolean isOpen() {
        return isRunning;
    }
    
    public int getPort() {
        return serverSocket.getLocalPort();
    }
    
    public InetSocketAddress getInetSocketAddress() {
        return (InetSocketAddress)serverSocket.getLocalSocketAddress();
    }

    public WsParameters getParameters() {
        return wsp;
    }

    public void close(String closeReason) {
        Thread.currentThread().setPriority(MAX_PRIORITY);
        this.isRunning = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
//            e.printStackTrace();
        }
        // close associated connections            
        for (WsConnection connection : listConnections()) {
            connection.close(WsStatus.GOING_AWAY, closeReason);
        }
    }

    public void close() {
        close("Shutdown");
    }

    public WsConnection[] listConnections() {
        return connections.toArray(new WsConnection[0]);
    }

    @Override
    public void run() {
        listeners.add(this);
        if (!this.isRunning) {
            this.isRunning = true;
            while (this.isRunning) {
                try {
// serverSocket SO_TIMEOUT = 0 by WebSocket creator
                    Socket socket = serverSocket.accept();
                    WsConnection conn
                            = new WsConnection(socket, handler, isSecure, wsp);
                    socket.setSoTimeout(wsp.handshakeSoTimeout);
                    conn.connections = this.connections;
                    conn.start();
                } catch (Exception e) {
                    if (this.isRunning) {
                        handler.onError(null, e);
                        this.close("Listener abnormal closure");
                    }
                    break;
                }
            }
        }
        listeners.remove(this);
    }

}
