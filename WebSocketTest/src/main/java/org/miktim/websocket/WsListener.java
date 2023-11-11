/*
 * WsListener. WebSocket listener, MIT (c) 2020-2023 miktim@mail.ru
 *
 * Accepts sockets, creates and starts connection threads.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.IOException;
//import java.io.InterruptedIOException;
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
    List<WsListener> listeners = null;
    private final List<WsConnection> connections
            = Collections.synchronizedList(new ArrayList<WsConnection>());

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
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }

    public WsParameters getParameters() {
        return wsp;
    }

    public void close() {
        close("Shutdown");
    }

    public void close(String closeReason) {
        Thread.currentThread().setPriority(MAX_PRIORITY);
        this.isRunning = false;
        closeServerSocket();
        // close associated connections            
        for (WsConnection connection : listConnections()) {
            connection.close(WsStatus.GOING_AWAY, closeReason);
        }
    }

    void closeServerSocket() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public WsConnection[] listConnections() {
        return connections.toArray(new WsConnection[0]);
    }
    
    public void interrupt() {
        closeServerSocket();
    }

    @Override
    public void run() {
        listeners.add(this); // add to creator list
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
                    isRunning = false;
                    closeServerSocket();
                    handler.onError(null, e);
                    return;
                }
                break;
            }
        }
        listeners.remove(this);
    }

}
