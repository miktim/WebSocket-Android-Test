/*
 * The MIT License
 *
 * Copyright 2023 miktim.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.miktim.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

public class WsInputStream extends InputStream {

        ArrayDeque<byte[]> frames; // Queue?
        byte[] frame = new byte[0];
        boolean isText;
        int iByte = 0;
        long available;

        WsInputStream(ArrayDeque<byte[]> frames, long messageLen, boolean isText) {
            super();
            this.frames = frames;
            this.isText = isText;
            available = messageLen;
        }

        private boolean getFrame() {
            if (frames.size() > 0) {
                frame = frames.poll();
                iByte = 0;
                return true;
            }
            return false;
        }

        public boolean isText() {
            return isText;
        }

        @Override
        public int available() {
            return (int) available;
        }

        @Override
        public int read() {//throws IOException {
            do {
                if (iByte < frame.length) {
                    available--;
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

