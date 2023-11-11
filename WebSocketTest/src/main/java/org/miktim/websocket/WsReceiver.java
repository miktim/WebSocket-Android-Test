/*
 * WsReceiver. Provides reading and processing of WebSocket frames, MIT (c) 2020-2023 miktim@mail.ru
 *
 * Created: 2021-01-29
 */
package org.miktim.websocket;

import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

class WsReceiver extends Thread {

    final private WsConnection conn;
    final private ArrayBlockingQueue<WsInputStream> messageQueue;

    private int opData = OP_FINAL; // data frame opcode
    private final byte[] payloadMask = new byte[8]; // mask & temp buffer for payloadLength
    private long payloadLength;
    private long messageLength;
    private boolean pingFrameSent = false;
    private boolean maskedPayload;
    private ArrayDeque<byte[]> messageFrames = null;

    WsReceiver(WsConnection conn, ArrayBlockingQueue<WsInputStream> messageQueue) {
        this.conn = conn;
        this.messageQueue = messageQueue;
    }

    static final int OP_FINAL = 0x80;
    static final int OP_CONTINUATION = 0x0;
    static final int OP_TEXT = 0x1;
    static final int OP_BINARY = 0x2;
    static final int OP_TEXT_FINAL = 0x81;
    static final int OP_BINARY_FINAL = 0x82;
    static final int OP_CLOSE = 0x88;
    static final int OP_PING = 0x89;
    static final int OP_PONG = 0x8A;
    static final int OP_EXTENSIONS = 0x70;
    static final int MASKED_DATA = 0x80;
    static final byte[] PING_PAYLOAD = "PingPong".getBytes();
    static final byte[] EMPTY_PAYLOAD = new byte[0];

    @Override
    public void run() {
        conn.status.wasClean = false;
        while (!conn.status.wasClean) {
            try {
                int b1 = conn.inStream.read();
                int b2 = conn.inStream.read();
                if ((b1 | b2) == -1) {
                    throw new EOFException("Unexpected EOF");
                }
                if ((b1 & OP_EXTENSIONS) != 0) {
                    throw new ProtocolException("Unsupported extension");
                }
// client MUST mask the data, server - MUST NOT
                maskedPayload = (b2 & MASKED_DATA) != 0;
                if (Boolean.compare(conn.isClientSide(), maskedPayload) == 0) {
                    conn.closeDueTo(WsStatus.PROTOCOL_ERROR, "Mask mismatch",
                            new ProtocolException());
                }

                readHeader(b2);

// check frame op sequence
                switch (b1) {
                    case OP_BINARY:
                    case OP_TEXT:
                    case OP_BINARY_FINAL:
                    case OP_TEXT_FINAL:
                        if ((opData & OP_FINAL) != 0) {
                            opData = b1;
                            messageFrames = new ArrayDeque<byte[]>();
                            messageLength = 0L;
                            dataFrame();
                            break;
                        }
                    case OP_CONTINUATION:
                        if ((opData & OP_FINAL) == 0) {
                            dataFrame();
                            break;
                        }
                    case OP_FINAL:
                        if ((opData & OP_FINAL) == 0) {
                            opData |= OP_FINAL;
                            dataFrame();
                            break;
                        }
                    case OP_CLOSE:
                    case OP_PING:
                    case OP_PONG:
                        controlFrame(b1);
                        break;
                    default:
                        throw new ProtocolException("Unexpected opcode");
                }

            } catch (SocketTimeoutException e) {
                if (conn.isOpen() && conn.wsp.pingEnabled && !pingFrameSent) {
                    pingFrameSent = true;
                    try {
                        conn.sendControlFrame(OP_PING, PING_PAYLOAD, PING_PAYLOAD.length);
                    } catch (IOException ex) {
                        conn.closeDueTo(WsStatus.ABNORMAL_CLOSURE, e.getMessage(), e);
                        break; // exit 
                    }
                } else {
                    conn.closeDueTo(WsStatus.ABNORMAL_CLOSURE, "Timeout", e);
                    break; // exit
                }
            } catch (ProtocolException e) {
                conn.closeDueTo(WsStatus.PROTOCOL_ERROR, e.getMessage(), e);
                break;
            } catch (Exception e) {
                conn.closeDueTo(WsStatus.ABNORMAL_CLOSURE, e.getMessage(), e);
                break;
            } catch (Error e) {
                e.printStackTrace();
                conn.closeDueTo(WsStatus.INTERNAL_ERROR, "Internal error", e);
                break;
            }
        }
// exit 
        messageFrames = null;
// TODO: wake up connection        
        messageQueue.clear();
//        ((Thread) conn).interrupt();
    }

    void readHeader(int b2) throws IOException {
// get frame payload length
        payloadLength = b2 & 0x7F;
        int toRead = 0;
        if (payloadLength == 126L) {
            toRead = 2;
        } else if (payloadLength == 127L) {
            toRead = 8;
        }
        if (toRead > 0) {
            if (conn.readFully(conn.inStream, payloadMask, 0, toRead) != toRead) {
                throw new EOFException("Unexpected EOF");
            }
            payloadLength = 0L;
            for (int i = 0; i < toRead; i++) {
                payloadLength <<= 8;
                payloadLength += (payloadMask[i] & 0xFF);
            }
        }
// get payload mask
        maskedPayload = (b2 & MASKED_DATA) != 0;
        if (maskedPayload) {
            toRead = 4;
            if (conn.readFully(conn.inStream, payloadMask, 0, toRead) != toRead) {
                throw new EOFException("Unexpected EOF");
            }
        }
    }

    boolean dataFrame() throws IOException {
        messageLength += payloadLength;
        if (messageLength > conn.wsp.maxMessageLength) {
            IOException e = new IOException("Message too big");
            conn.closeDueTo(WsStatus.MESSAGE_TOO_BIG, e.getMessage(), e);
            messageFrames = null;
        }
        if(messageFrames == null) {
            skipPayload();
            return false;
        }
        messageFrames.add(readPayload());
        if ((opData & OP_FINAL) != 0) {
            messageQueue.add(new WsInputStream(
                    new ArrayDeque<byte[]>(messageFrames),
                    messageLength,
                    (opData & OP_TEXT) != 0)
            );
            messageFrames = null;
        }
        return true;
    }

    byte[] readPayload() throws IOException {
// read frame payload
        byte[] framePayload = new byte[(int) payloadLength];
        if (conn.readFully(conn.inStream, framePayload, 0, (int) payloadLength)
                != framePayload.length) {
            throw new EOFException("Unexpected EOF");
        }
// unmask frame payload
        if (maskedPayload) {
            conn.umaskPayload(payloadMask, framePayload, 0, framePayload.length);
        }
        return framePayload;
    }

    boolean controlFrame(int b1) throws IOException {
        if (payloadLength > 125L) {
            throw new ProtocolException("Payload too big");
        }

        byte[] framePayload = readPayload();

        switch (b1) {
            case OP_PONG: {
                if (pingFrameSent
                        && Arrays.equals(framePayload, PING_PAYLOAD)) {
                    pingFrameSent = false;
                    return true;
                } else {
                    throw new ProtocolException("Unexpected pong");
                }
            }
            case OP_PING:
                if (conn.isOpen()) {
                    conn.sendControlFrame(OP_PONG, framePayload, framePayload.length);
                }
                return true;
            case OP_CLOSE:  // close handshake
                if (conn.isOpen()) {
                    conn.status.remotely = true;
                    conn.socket.setSoTimeout(conn.wsp.handshakeSoTimeout);
                    // send approval
                    conn.sendControlFrame(OP_CLOSE, framePayload, framePayload.length);                    // extract status code and reason
                    if (framePayload.length > 1) {
                        conn.status.code = ((framePayload[0] & 0xFF) << 8)
                                + (framePayload[1] & 0xFF);
                        conn.status.reason = new String(framePayload,
                                 2, framePayload.length - 2, "UTF-8");
                    } else {
                        conn.status.code = WsStatus.NO_STATUS;
                    }
                }
                conn.status.wasClean = true;
                return true;
            default:
                throw new ProtocolException("Unsupported opcode");
        }

    }

    void skipPayload() throws IOException {
        while (payloadLength > 0) {
            payloadLength -= conn.inStream.skip(payloadLength);
        }
    }

}
