/*
 * WebSocket. MIT (c) 2020-2023 miktim@mail.ru
 *
 * Creates ServerSocket/Socket (bind, connect).
 * Creates and starts listener/connection threads.
 *
 * Release notes:
 * - Java SE 7+, Android compatible;
 * - RFC-6455: https://tools.ietf.org/html/rfc6455 ;
 * - supported WebSocket version: 13;
 * - WebSocket extensions not supported;
 * - supports plain socket/TLS connections;
 * - stream-based messaging.
 *
 * Created: 2020-06-06
 */
package org.miktim.websocket;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class WebSocket {

    public static String VERSION = "3.4.5";
    private InetAddress bindAddress = null;
    private final List<WsConnection> connections = Collections.synchronizedList(new ArrayList<WsConnection>());
    private final List<WsListener> listeners = Collections.synchronizedList(new ArrayList<WsListener>());
    private File keyStoreFile = null;
    private String keyStorePassword = null;

    //   private String keyStoreAlgorithm = "BNC"; // "SunX509"
    public WebSocket() throws NoSuchAlgorithmException {
        MessageDigest.getInstance("SHA-1"); // check algorithm exists
    }

    public WebSocket(InetAddress bindAddr) throws SocketException {
        super();
        if (NetworkInterface.getByInetAddress(bindAddr) == null) {
            throw new BindException("Not interface");
        }
        bindAddress = bindAddr;
    }

    public static void setTrustStore(String jksFile, String passphrase) {
        System.setProperty("javax.net.ssl.trustStore", jksFile);
        System.setProperty("javax.net.ssl.trustStorePassword", passphrase);
    }

    public static void setKeyStore(String jksFile, String passphrase) {
        System.setProperty("javax.net.ssl.keyStore", jksFile);
        System.setProperty("javax.net.ssl.keyStorePassword", passphrase);
    }

    public void setKeyFile(File keyfile, String password) {//, String algorithm) {
        keyStoreFile = keyfile;
        keyStorePassword = password;
//        keyStoreAlgorithm = algorithm;
    }

    public void resetKeyFile() {
        keyStoreFile = null;
        keyStorePassword = null;
    }

    public InetAddress getBindAddress() {
        return bindAddress;
    }

    public WsConnection[] listConnections() {
        return connections.toArray(new WsConnection[0]);
    }

    public WsListener[] listListeners() {
        return listeners.toArray(new WsListener[0]);
    }

    public void closeAll(String closeReason) {
// close WebSocket listeners/connections 
        for (WsListener listener : listListeners()) {
            listener.close(closeReason);
        }
        for (WsConnection conn : listConnections()) {
            conn.close(WsStatus.GOING_AWAY, closeReason);
        }
    }

    public void closeAll() {
        closeAll("");
    }

    public WsListener listen(int port, WsHandler handler, WsParameters wsp)
            throws IOException, GeneralSecurityException {
        return startListener(port, handler, false, wsp);
    }

    public WsListener listenSafely(int port, WsHandler handler, WsParameters wsp)
            throws IOException, GeneralSecurityException {
        return startListener(port, handler, true, wsp);
    }

    synchronized WsListener startListener(int port, WsHandler handler, boolean isSecure, WsParameters wsp)
            throws IOException, GeneralSecurityException {
        if (handler == null || wsp == null) {
            throw new NullPointerException();
        }
        wsp = wsp.deepClone();

        ServerSocket serverSocket;
        if (isSecure) {
            ServerSocketFactory serverSocketFactory;
            if (this.keyStoreFile != null) {
                serverSocketFactory = getSSLContext(false)
                        .getServerSocketFactory();
            } else {
                serverSocketFactory = SSLServerSocketFactory.getDefault();
            }
            serverSocket = serverSocketFactory
                    .createServerSocket(port, wsp.backlog, bindAddress);

            SSLParameters sslp = wsp.getSSLParameters();
            if (sslp != null) {
                ((SSLServerSocket) serverSocket).setNeedClientAuth(sslp.getNeedClientAuth());
                ((SSLServerSocket) serverSocket).setEnabledProtocols(sslp.getProtocols());
                ((SSLServerSocket) serverSocket).setWantClientAuth(sslp.getWantClientAuth());
                ((SSLServerSocket) serverSocket).setEnabledCipherSuites(sslp.getCipherSuites());
// TODO: removed code Android API 24 to API 16

//            ((SSLServerSocket) serverSocket).setSSLParameters(wsp.sslParameters);
            }
        } else {
            serverSocket = new ServerSocket(port, wsp.backlog, bindAddress);
        }

        serverSocket.setSoTimeout(0);
        WsListener listener = 
                new WsListener(serverSocket, handler, isSecure, wsp);
        listener.listeners = listeners;
        listener.start();
        return listener;
    }

// https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/samples/sockets/server/ClassFileServer.java
    synchronized private SSLContext getSSLContext(boolean isClient)
            throws IOException, GeneralSecurityException {
//            throws NoSuchAlgorithmException, KeyStoreException,
//            FileNotFoundException, IOException, CertificateException,
//            UnrecoverableKeyException {
        SSLContext ctx;
        KeyManagerFactory kmf;
        KeyStore ks;// = KeyStore.getInstance(KeyStore.getDefaultType());

        String ksPassphrase = this.keyStorePassword;
        File ksFile = this.keyStoreFile;
//            if (ksFile == null) {
//                ksPassphrase = System.getProperty("javax.net.ssl.keyStorePassword");
//                ksFile = new File(System.getProperty("javax.net.ssl.keyStore"));
//            }
        char[] passphrase = ksPassphrase.toCharArray();

        ctx = SSLContext.getInstance("TLS");
        kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()); // java:"SunX509", android:"PKIX"
        ks = KeyStore.getInstance(KeyStore.getDefaultType()); // android: BKS, java: JKS
        FileInputStream ksFis = new FileInputStream(ksFile);
        ks.load(ksFis, passphrase);
        ksFis.close();
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        );
        tmf.init(ks);

        if (isClient) {
            ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
        } else {
            ctx.init(kmf.getKeyManagers(), null, null);
        }
        return ctx;
    }

    synchronized public WsConnection connect(String uri, WsHandler handler, WsParameters wsp)
            throws URISyntaxException, IOException, GeneralSecurityException {
        if (uri == null || handler == null || wsp == null) {
            throw new NullPointerException();
        }
        wsp = wsp.deepClone();
        URI requestURI = new URI(uri);
        String scheme = requestURI.getScheme();
        String host = requestURI.getHost();
        if (host == null || scheme == null) {
            throw new URISyntaxException(uri, "Scheme and host required");
        }
        if (!(scheme.equals("ws") || scheme.equals("wss"))) {
            throw new URISyntaxException(uri, "Unsupported scheme");
        }

        Socket socket;
        boolean isSecure = scheme.equals("wss");
        SSLSocketFactory factory;

        if (isSecure) {
            if (this.keyStoreFile != null) {
                factory = getSSLContext(true).getSocketFactory();
            } else {
                factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }
            socket = (SSLSocket) factory.createSocket();
            if (wsp.sslParameters != null) {
                ((SSLSocket) socket).setSSLParameters(wsp.sslParameters);
            }
        } else {
            socket = new Socket();
        }
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(bindAddress, 0));
        int port = requestURI.getPort();
        if (port < 0) {
            port = isSecure ? 443 : 80;
        }
        socket.connect(
                new InetSocketAddress(requestURI.getHost(), port), wsp.handshakeSoTimeout);

        WsConnection conn = new WsConnection(socket, handler, requestURI, wsp);
        conn.connections = this.connections;
        conn.start();
        return conn;
    }

}
