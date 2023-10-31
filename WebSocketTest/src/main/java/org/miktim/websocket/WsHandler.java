/*
 * WsHandler. WebSocket connection events handler, MIT (c) 2020-2023 miktim@mail.ru
 *
 * There are three scenarios for handling events:
 * - onError, on the listener side when the ServerSocket fails;
 * - onError - onClose, when SSL/WebSocket handshake fails;
 * - onOpen - [onMessage - onMessage - ...] - [onError] - onClose.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.InputStream;

public interface WsHandler {
    public void onOpen(WsConnection conn, String subProtocol);
//   - the second argument is the negotiated WebSocket subprotocol or null.    

    public void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text);
//   - the WebSocket message is represented by an input stream of binary data or UTF-8 characters;
//   - exiting the handler closes the stream (not connection!).

    public void onError(WsConnection conn, Throwable e);
//   - any error closes the WebSocket connection;
//   - allocating large buffers may throw an OutOfMemoryError;
//   - conn is null in the listener handler when ServerSocket fails.
//     The listener and all associated connections will be closed
    
    public void onClose(WsConnection conn, WsStatus closeStatus);
}
