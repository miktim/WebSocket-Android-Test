/*
 * WsInputStreamThread. MIT (c) 2023 miktim@mail.ru
 * Provides asynchronous processing of incoming WebSocket messages
 */

package org.miktim.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

class WsInputStreamThread extends Thread {

    final WsInputStream wis;
    final boolean isText;
    final WsConnection conn;

    WsInputStreamThread(WsConnection conn, ArrayList<byte[]> frames, boolean isText) {
        this.setDaemon(true);
        this.conn = conn;
        wis = new WsInputStream(frames);
        this.isText = isText;
    }

    @Override
    public void run() {
        conn.handler.onMessage(conn, wis, isText);
        try {
            wis.close();
        } catch (IOException e) {
        };
    }
}

class WsInputStream extends InputStream {

    ArrayList<byte[]> frames;
    byte[] frame = new byte[0];
    int iFrame = 0;
    int iByte = 0;

    WsInputStream(ArrayList<byte[]> frames) {
        super();
        this.frames = frames;
    }

    private boolean getFrame() {
        if (iFrame < frames.size()) {
            frame = frames.get(iFrame++);
            iByte = 0;
            return true;
        }
        return false;
    }

    @Override
    public int read() {//throws IOException {
        do {
            if (iByte < frame.length) {
                return ((int) frame[iByte++]) & 0xFF;
            }
        } while (getFrame());
        return -1; // end of message
    }

    @Override
    public void close() throws IOException {
        frames.clear();
        frame = new byte[0];
    }
}

