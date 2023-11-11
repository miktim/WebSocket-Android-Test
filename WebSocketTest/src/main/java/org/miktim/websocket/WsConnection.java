/*
 * WsConnection. WebSocket connection, MIT (c) 2020-2023 miktim@mail.ru
 *
 * SSL/WebSocket handshaking. Messaging. Events handling.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocket;

public class WsConnection extends Thread {

    public static final int MESSAGE_QUEUE_CAPACITY = 3;
    private static final String SERVER_AGENT = "WsLite/" + WebSocket.VERSION;

    final Socket socket;
    private WsHandler handler;
    private final boolean isSecure; // SSL connection
    private final boolean isClientSide;
    private URI requestURI;
    private String subProtocol = null; // handshaked WebSocket subprotocol
    final WsParameters wsp;
    final WsStatus status = new WsStatus();
    List<WsConnection> connections = null; // list of connections to a websocket or listener

    // sending data in binary format or UTF-8 encoded text
    public void send(InputStream is, boolean isUTF8Text) throws IOException {
        syncSend(is, isUTF8Text);
    }

    // send binary data
    public void send(byte[] message) throws IOException {
        syncSend(new ByteArrayInputStream(message), false);
    }

    // send text
    public void send(String message) throws IOException {
        syncSend(new ByteArrayInputStream(message.getBytes("UTF-8")), true);
    }

    // Returns handshaked WebSocket subprotocol
    public String getSubProtocol() {
        return subProtocol;
    }

    public WsStatus getStatus() {
        return status.deepClone();
    }

    public boolean isOpen() {
        return status.code == WsStatus.IS_OPEN;
    }

    public boolean isSecure() {
        return isSecure;
    }

    public boolean isClientSide() {
        return isClientSide;
    }

    public WsConnection[] listConnections() {
        return isClientSide ? new WsConnection[]{this}
                : connections.toArray(new WsConnection[0]);
    }

    public synchronized void setHandler(WsHandler h) {
        if (isOpen()) {
            handler.onClose(this, status);
            handler = h;
            h.onOpen(this, subProtocol);
        }
    }

    public WsParameters getParameters() {
        return wsp;
    }

    // Returns remote host name or null
    public String getPeerHost() {
        try {
            if (isClientSide) {
                return requestURI.getHost();
            }
            if (isSecure) {
                return ((SSLSocket) socket).getSession().getPeerHost();
            }
// TODO: removed code Android API 19 to API 16

//          else {
//                return ((InetSocketAddress) socket.getRemoteSocketAddress()).getHostString();
//            }
        } catch (Exception e) {
        }
        return null;
    }

    // Returns the connected port
    public int getPort() {
        return socket.getPort();
    }

    // Returns http request path
    public String getPath() {
        if (this.requestURI != null) {
            return requestURI.getPath();
        }
        return null;
    }

    // Returns http request query
    public String getQuery() {
        if (this.requestURI != null) {
            return requestURI.getQuery();
        }
        return null;
    }

    public String getSSLSessionProtocol() {
        if (this.isSecure() && this.isSocketOpen()) {
            return ((SSLSocket) this.socket).getSession().getProtocol();
        }
        return null;
    }

    /*
        // allocate buffer from 1/4 to payloadBufferLength
        byte[] getBuffer(byte[] buffer) {
            if (buffer == null) {
                buffer = EMPTY_PAYLOAD;
            }
            int mpbl = WsParameters.MIN_PAYLOAD_BUFFER_LENGTH;
            int pbl = wsp.getPayloadBufferLength();
            if (buffer.length >= pbl) {
                return buffer;
            }
            if (buffer.length == 0) {
                return new byte[(pbl / 4 > mpbl * 4) ? pbl / 4 : pbl];
            }
            return new byte[Math.min(pbl, buffer.length * 2)];
        }
     */
    private synchronized void syncSend(InputStream is, boolean isText)
            throws IOException {
        if (!isOpen()) {
            throw new SocketException("WebSocket is not open");
        }
        int op = isText ? WsReceiver.OP_TEXT : WsReceiver.OP_BINARY;
        try {
            byte[] buf = new byte[wsp.payloadBufferLength];
            int len = 0;
            while ((len = this.readFully(is, buf, 0, buf.length)) == buf.length) {
                sendFrame(op, buf, buf.length);
                op = WsReceiver.OP_CONTINUATION;
            }
// be sure to send the final frame even if eof is detected (payload length = 0)!            
            sendFrame(op | WsReceiver.OP_FINAL, buf, len >= 0 ? len : 0); //
        } catch (IOException e) {
            closeDueTo(WsStatus.INTERNAL_ERROR, "Send failed", e);
            throw e;
        }
    }

// Close notes:
// - the closing code outside 1000-4999 is replaced by 1005 (NO_STATUS)
//   and the reason is ignored; 
// - a reason that is longer than 123 bytes is truncated;
// - closing the connection blocks outgoing messages (send methods throw IOException);
// - isOpen() returns false;
// - incoming messages are available until the closing handshake completed.
    public void close() {
        close(WsStatus.NO_STATUS, "");
    }

    public synchronized void close(int code, String reason) {
        if (isOpen()) {
            code = (code < 1000 || code > 4999) ? WsStatus.NO_STATUS : code;
            byte[] payload = new byte[125];
            byte[] byteReason = new byte[0];
            try {
                byteReason = (reason == null ? "" : reason).getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            int payloadLen = 0;
            if (code != WsStatus.NO_STATUS) {
                status.reason = reason;
                payload[0] = (byte) (code >>> 8);
                payload[1] = (byte) (code & 0xFF);
                byteReason = Arrays.copyOf(
                        byteReason, Math.min(byteReason.length, 123));
                System.arraycopy(byteReason, 0, payload, 2, byteReason.length);
                payloadLen = 2 + byteReason.length;
            }
            try {
                sendControlFrame(WsReceiver.OP_CLOSE, payload, payloadLen);
                status.code = code; // disable output
                status.remotely = false;
                socket.setSoTimeout(wsp.handshakeSoTimeout);
            } catch (IOException e) {
                closeSocket();
                return;
            }
// force closing socket            
            (new Timer(true)).schedule(new TimerTask() { // daemon timer
                @Override
                public void run() {
                    closeSocket();
                }
            }, wsp.handshakeSoTimeout);
        }
    }

    // WebSocket listener connection constructor
    WsConnection(Socket s, WsHandler h, boolean secure, WsParameters wsp) {
        this.isClientSide = false;
        this.socket = s;
        this.handler = h;
        this.isSecure = secure;
        this.wsp = wsp;
    }

    // WebSocket client connection constructor
    WsConnection(Socket s, WsHandler h, URI uri, WsParameters wsp) {
        this.isClientSide = true;
        this.socket = s;
        this.handler = h;
        this.requestURI = uri;
        this.isSecure = uri.getScheme().equals("wss");
        this.wsp = wsp;
    }

    InputStream inStream;
    OutputStream outStream;

    @Override
    public void run() {
        connections.add(this);
        if (waitConnection()) {
            this.handler.onOpen(this, subProtocol);
            waitMessages(MESSAGE_QUEUE_CAPACITY);
        }
        if (status.error != null) {
            handler.onError(this, status.error);
        }
        handler.onClose(this, getStatus());
        if (!isClientSide) {
            closeSocket();
        }
        connections.remove(this);
    }

    private boolean waitConnection() {
        try {
            inStream = new BufferedInputStream(socket.getInputStream());
            outStream = new BufferedOutputStream(socket.getOutputStream());
            if (isClientSide) {
                handshakeServer();
            } else {
                handshakeClient();
            }
            socket.setSoTimeout(wsp.connectionSoTimeout);
            status.code = WsStatus.IS_OPEN;
            return true;
        } catch (Exception e) {
            status.reason = "Failed to connect";
            status.error = e;
            status.code = WsStatus.PROTOCOL_ERROR;
            return false;
        }
    }

    ArrayBlockingQueue<WsInputStream> messageQueue;

    void waitMessages(int queueCapacity) {
        messageQueue = new ArrayBlockingQueue<WsInputStream>(queueCapacity);
        WsReceiver receiver = new WsReceiver(this, messageQueue);
        receiver.start();
        WsInputStream is;
        while (receiver.isAlive()) {
            try {
                is = messageQueue.poll(500L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                continue;
            }
            if (is == null) {
                continue;
            }
            handler.onMessage(this, is, is.isText());
        }
        messageQueue.clear();
    }

    private void handshakeClient()
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        HttpHead requestHead = (new HttpHead()).read(inStream);
        String[] parts = requestHead.get(HttpHead.START_LINE).split(" ");
        this.requestURI = new URI(parts[1]);
        String upgrade = requestHead.get("Upgrade");
        String key = requestHead.get("Sec-WebSocket-Key");

        HttpHead responseHead = (new HttpHead()).set("Server", SERVER_AGENT);
        if (parts[0].equals("GET")
                && upgrade != null && upgrade.equals("websocket")
                && key != null
                && setSubprotocol(
                        requestHead.getValues("Sec-WebSocket-Protocol"), responseHead)) {
            responseHead
                    .set(HttpHead.START_LINE, "HTTP/1.1 101 Upgrade")
                    .set("Upgrade", "websocket")
                    .set("Connection", "Upgrade,keep-alive")
                    .set("Sec-WebSocket-Accept", sha1Hash(key))
                    .set("Sec-WebSocket-Version", "13")
                    .write(outStream);
        } else {
            responseHead
                    .set(HttpHead.START_LINE, "HTTP/1.1 400 Bad Request")
                    .set("Connection", "close")
                    .write(outStream);
            status.remotely = false;
            throw new ProtocolException("WebSocket handshake failed");
        }
    }

    private boolean setSubprotocol(String[] requestedSubps, HttpHead rs) {
        if (requestedSubps == null) {
            return true;
        }
        if (wsp.subProtocols != null) {
            for (String agreedSubp : requestedSubps) {
                for (String subp : wsp.subProtocols) {
                    if (agreedSubp.equals(subp)) {
                        this.subProtocol = agreedSubp;
                        rs.set("Sec-WebSocket-Protocol", agreedSubp); // response headers
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void handshakeServer()
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        String key = base64Encode(randomBytes(16));

        String requestPath = (requestURI.getPath() == null ? "/" : requestURI.getPath())
                + (requestURI.getQuery() == null ? "" : "?" + requestURI.getQuery());
        requestPath = (new URI(requestPath)).toASCIIString();
        if (!requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }
        String host = requestURI.getHost()
                + (requestURI.getPort() > 0 ? ":" + requestURI.getPort() : "");
//        host = (new URI(host)).toASCIIString(); // URISyntaxException on IP addr
        HttpHead requestHead = (new HttpHead())
                .set(HttpHead.START_LINE, "GET " + requestPath + " HTTP/1.1")
                .set("Host", host)
                .set("Origin", requestURI.getScheme() + "://" + host)
                .set("Upgrade", "websocket")
                .set("Connection", "Upgrade,keep-alive")
                .set("Sec-WebSocket-Key", key)
                .set("Sec-WebSocket-Version", "13")
                .set("User-Agent", SERVER_AGENT);
        if (wsp.subProtocols != null) {
            requestHead.setValues("Sec-WebSocket-Protocol", wsp.subProtocols);
        }
        requestHead.write(outStream);

        HttpHead responseHead = (new HttpHead()).read(inStream);
        this.subProtocol = responseHead.get("Sec-WebSocket-Protocol");
        if (!(responseHead.get(HttpHead.START_LINE).split(" ")[1].equals("101")
                && responseHead.get("Sec-WebSocket-Accept").equals(sha1Hash(key))
                && checkSubprotocol())) {
            status.remotely = false;
            throw new ProtocolException("WebSocket handshake failed");
        }
    }

    private boolean checkSubprotocol() {
        if (this.subProtocol == null && wsp.subProtocols == null) {
            return true; // 
        }
        if (wsp.subProtocols != null) {
            for (String subp : wsp.subProtocols) {
                if (String.valueOf(subp).equals(this.subProtocol)) { // subp can be null?
                    return true;
                }
            }
        }
        return false;
    }

    // generate "random" mask/key
    byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        long l = Double.doubleToRawLongBits(Math.random());
        while (--len >= 0) {
            b[len] = (byte) l;
            l >>= 1;
        }
        return b;
    }

    private String sha1Hash(String key) throws NoSuchAlgorithmException {
        return base64Encode(MessageDigest.getInstance("SHA-1").digest(
                (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes()));
    }

    private static final byte[] B64_BYTES = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes();

    public static String base64Encode(byte[] b) {
        byte[] s = new byte[((b.length + 2) / 3 * 4)];
        int bi = 0;
        int si = 0;
        while (bi < b.length) {
            int k = Math.min(3, b.length - bi);
            int bits = 0;
            for (int j = 0, shift = 16; j < k; j++, shift -= 8) {
                bits += ((b[bi++] & 0xFF) << shift);
            }
            for (int j = 0, shift = 18; j <= k; j++, shift -= 6) {
                s[si++] = B64_BYTES[(bits >> shift) & 0x3F];
            }
        }
        while (si < s.length) {
            s[si++] = (byte) '=';
        }
        return new String(s);
    }

    static int readFully(InputStream is, byte[] buf, int off, int len)
            throws IOException {
        int bytesCnt = 0;
        for (int n = is.read(buf, off, len); n >= 0 && bytesCnt < len;) {
            bytesCnt += n;
            n = is.read(buf, off + bytesCnt, len - bytesCnt);
        }
        return bytesCnt;
    }

    boolean isSocketOpen() {
        return !(this.socket == null || this.socket.isClosed());
    }

    void closeSocket() {
        if (this.isSocketOpen()) {
            try {
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void closeDueTo(int closeCode, String reason, Throwable e) {
        if (isOpen()) {
            status.error = e;
            close(closeCode, reason);
        }
    }

    void sendControlFrame(int opFrame, byte[] payload, int payloadLen)
            throws IOException {
        sendFrame(opFrame, Arrays.copyOf(payload, payloadLen), payloadLen);
    }

    synchronized void sendFrame(int opFrame, byte[] payload, int payloadLen)
            throws IOException {
        if (!isOpen()) { //
            throw new SocketException("WebSocket is not open");
        }
// client MUST mask payload, server MUST NOT        
        boolean masked = this.isClientSide;
        byte[] header = new byte[14]; //hopefully initialized with zeros

        header[0] = (byte) opFrame;
        int headerLen = 2;

        int tempLen = payloadLen;
        if (tempLen < 126) {
            header[1] = (byte) (tempLen);
        } else if (tempLen < 0x10000) {
            header[1] = (byte) 126;
            header[2] = (byte) (tempLen >>> 8);
            header[3] = (byte) tempLen;
            headerLen += 2;
        } else {
            header[1] = (byte) 127;
            headerLen += 4; // skip 4 zero bytes of 64bit payload length
            for (int i = 3; i > 0; i--) {
                header[headerLen + i] = (byte) (tempLen & 0xFF);
                tempLen >>>= 8;
            }
            headerLen += 4;
        }
        
        if (masked) {
            header[1] |= WsReceiver.MASKED_DATA;
            byte[] mask = randomBytes(4);
            System.arraycopy(mask, 0, header, headerLen, 4);
            headerLen += 4;
            umaskPayload(mask, payload, 0, payloadLen);
        }

        try {
            outStream.write(header, 0, headerLen);
            outStream.write(payload, 0, payloadLen);
            outStream.flush();
        } catch (IOException e) {
            this.status.code = WsStatus.ABNORMAL_CLOSURE;
            throw e;
        }
    }

// unmask/mask payload
    static void umaskPayload(byte[] mask, byte[] payload, int off, int len) {
        for (int i = 0; i < len; i++) {
            payload[off++] ^= mask[i & 3];
        }
    }

}
