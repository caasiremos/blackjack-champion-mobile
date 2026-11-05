package com.piga21.temsoft;

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

import com.piga21.points.GamePoint;
import com.piga21.sockets.WebSocketHandler;
import com.piga21.utils.Constants;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class AndroidWebSocketHandler implements WebSocketHandler {
    private static final String TAG = "GameWebSocket";
    private static final int RECONNECT_DELAY_MS = 3000;
    private static final int MAX_RECONNECT_DELAY_MS = 30000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;
    private final String apiToken;
    public final int playerId;

    private final Activity activity;
    private WebSocket socket;
    private int reconnectAttempts = 0;
    private boolean closedByApp = false; // the app went offline on purpose: don't reconnect
    private volatile boolean signedIn = false; // the server has authenticated this connection
    private volatile String sendAfterSignIn; // e.g. accepting a challenge from a notification

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
        closedByApp = false;

        Request request = new Request.Builder()
            .url(SOCKET_URL)
            .addHeader("Authorization", "Bearer " + apiToken)
            .addHeader("Origin", "https://admin.theagrotracker.com")
            .addHeader("User-Agent", "Piga21-Android")
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
                log("Message from ratchet: " + redact(message));
                if (message.contains("\"status\":\"error\"") && message.contains("Invalid credentials") && sessionRejectedListener != null) {
                    mainHandler.post(sessionRejectedListener);
                }
                // A challenge while the app is in the background becomes a notification
                ChallengeNotifier.onServerMessage(activity.getApplicationContext(), message);
                if (message.contains("\"action\":\"authenticated\"")) {
                    signedIn = true;
                    String waiting = sendAfterSignIn;
                    sendAfterSignIn = null;
                    if (waiting != null) webSocket.send(waiting);
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
                signedIn = false;
                if (socket == webSocket) socket = null;
                notifyDisconnected(false);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                log("Connection failed: " + t.getMessage());
                signedIn = false;
                if (socket == webSocket) socket = null;
                notifyError(t.getMessage());
                attemptReconnect();
            }
        };
    }

    /**
     * Keep trying for as long as the app is running, so the player comes back online after a
     * network drop: quickly at first, then every half a minute.
     */
    private void attemptReconnect() {
        if (closedByApp) return;
        reconnectAttempts++;
        long delay = Math.min((long) RECONNECT_DELAY_MS * reconnectAttempts, MAX_RECONNECT_DELAY_MS);
        log("Reconnecting to ratchet (attempt " + reconnectAttempts + ", in " + delay + " ms)");
        notifyDisconnected(true);
        mainHandler.postDelayed(this::connect, delay);
    }

    @Override
    public void sendMessage(String message) {
        if (socket != null) {
            socket.send(message);
            log("Sent: " + redact(message));
        } else {
            Log.w(TAG, "Cannot send message - not connected");
        }
    }

    /**
     * Send now if the server has signed this connection in, otherwise as soon as it does.
     */
    public void sendWhenSignedIn(String message) {
        if (signedIn && socket != null) {
            sendMessage(message);
        } else {
            sendAfterSignIn = message;
        }
    }

    @Override
    public void setMessageListener(WebSocketHandler.MessageListener listener) {
        this.messageListener = listener;
    }

    @Override
    public void close() {
        closedByApp = true;
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

    /**
     * Keep login tokens and phone numbers out of the device log.
     */
    private static String redact(String message) {
        return message
            .replaceAll("\"token\"\\s*:\\s*\"[^\"]*\"", "\"token\":\"***\"")
            .replaceAll("\"phone_number\"\\s*:\\s*\"[^\"]*\"", "\"phone_number\":\"***\"");
    }
}
