package io.ngaigame.caasiremos.android;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import io.ngaigame.caasiremos.points.GamePoint;
import io.ngaigame.caasiremos.sockets.WebSocketHandler;
import io.ngaigame.caasiremos.utils.Constants;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class AndroidWebSocketHandler implements WebSocketHandler {
    private static final String TAG = "GameWebSocket";
    private static final int RECONNECT_DELAY_MS = 3000;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;
    private final String apiToken;
    public final int playerId;

    private final Activity activity;
    private WebSocket socket;
    private int reconnectAttempts = 0;

    private WebSocketHandler.MessageListener messageListener;



//    private static final String SOCKET_URL = "wss://admin.theagrotracker.com/app/9t1mw4qmxgfh8jhm89hu";
    private static final String SOCKET_URL = "wss://admin.theagrotracker.com/socket";

    private ConnectionListener connectionListener;
    private Runnable sessionRejectedListener;

    public interface ConnectionListener {
        void onConnected();

        void onDisconnected(boolean willReconnect);

        void onMessageReceived(String message);

        void onError(String error);
    }

    public AndroidWebSocketHandler(Activity activity) {
        this.activity = activity;
        SharedPreferences prefs = activity.getSharedPreferences(GamePoint.GAME_PREFS, Context.MODE_PRIVATE);
        this.apiToken = prefs.getString(GamePoint.API_TOKEN, null);
        this.playerId = prefs.getInt(Constants.PLAYER_ID, -1);

        this.client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }

    @Override
    public boolean isConnected() {
        return socket != null;
    }


    /**
     * Connects unless a connection is already open or being opened.
     */
    @Override
    public void connect() {
        if (socket != null) return;

        Request request = new Request.Builder()
            .url(SOCKET_URL)
            .addHeader("Authorization", "Bearer " + apiToken)
            .addHeader("Origin", "https://admin.theagrotracker.com")
            .addHeader("User-Agent", "Caasiremos-Android")
            .addHeader("X-Game-Version", "1.0")
            .build();

        socket = client.newWebSocket(request, createListener());
    }

    private WebSocketListener createListener() {
        return new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                log("Connected to ratchet");
                reconnectAttempts = 0;
                authenticate(webSocket);
                notifyConnected();
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String message) {
                log("Message from ratchet: " + message);
                if (message.contains("\"status\":\"error\"") && message.contains("Invalid credentials") && sessionRejectedListener != null) {
                    mainHandler.post(sessionRejectedListener);
                }
                notifyMessageReceived(message);
                // Send to core game logic
                if (messageListener != null) {
                    messageListener.onMessage(message);
                }
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                log("Connection closed: " + code + " - " + reason);
                if (socket == webSocket) socket = null;
                notifyDisconnected(false);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                log("Connection failed: " + t.getMessage());
                if (socket == webSocket) socket = null;
                notifyError(t.getMessage());
                attemptReconnect();
            }
        };
    }

    private void attemptReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            log("Reconnecting to ratchet (attempt " + reconnectAttempts + ")");
            notifyDisconnected(true);

            mainHandler.postDelayed(this::connect, RECONNECT_DELAY_MS);
        } else {
            log("Max reconnect attempts reached for ratchet");
            notifyDisconnected( false);
        }
    }

    @Override
    public void sendMessage(String message) {
        if (socket != null) {
            socket.send(message);
            log("Sent: " + message);
        } else {
            Log.w(TAG, "Cannot send message - not connected");
        }
    }

    @Override
    public void setMessageListener(WebSocketHandler.MessageListener listener) {
        this.messageListener = listener;
    }

    @Override
    public void close() {
        if (socket != null) {
            socket.close(1000, "Normal closure");
            socket = null;
        }
    }

    /**
     * Log this connection in with the current saved token (it may have changed since startup).
     */
    private void authenticate(WebSocket webSocket) {
        SharedPreferences prefs = activity.getSharedPreferences(GamePoint.GAME_PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString(GamePoint.API_TOKEN, null);
        int userId = prefs.getInt(Constants.PLAYER_ID, -1);
        if (token == null || userId < 0) return;

        try {
            JSONObject message = new JSONObject();
            message.put("action", "authenticate");
            message.put("user_id", userId);
            message.put("token", token);
            webSocket.send(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Couldn't build authenticate message", e);
        }
    }

    public void setSessionRejectedListener(Runnable listener) {
        this.sessionRejectedListener = listener;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    private void notifyConnected() {
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onConnected());
        }
    }

    private void notifyDisconnected(boolean willReconnect) {
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onDisconnected(willReconnect));
        }
    }

    private void notifyMessageReceived(String message) {
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onMessageReceived(message));
        }
    }

    private void notifyError(String error) {
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onError(error));
        }
    }

    private void log(String message) {
        Log.d(TAG, message);
    }
}
