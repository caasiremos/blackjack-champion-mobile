package io.ngaigame.caasiremos.sockets;

public interface WebSocketHandler {
    boolean isConnected();
    void connect();
    void sendMessage(String message);
    void setMessageListener(MessageListener listener);
    void close();

    interface MessageListener {
        void onMessage(String message);
    }
}
