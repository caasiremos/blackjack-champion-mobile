package com.piga21.temsoft;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

import java.util.Locale;

import com.piga21.R;

/**
 * While the app is in the background, a challenge from the server becomes a notification.
 * Tapping it accepts the challenge and opens the app, which goes straight to the game table.
 * The notification is taken down if the challenge is cancelled, expires or is answered.
 */
public class ChallengeNotifier {
    static final String EXTRA_ACCEPT_REQUEST = "accept_request_id";
    private static final String CHANNEL_ID = "challenges";
    private static final int NOTIFICATION_ID = 22;

    /** Whether the app is on screen; set by AndroidLauncher */
    static volatile boolean appInForeground = false;
    private static volatile String shownRequestId;

    /**
     * Look at every message from the server (on the socket's thread).
     */
    static void onServerMessage(Context context, String message) {
        String action;
        JSONObject json;
        try {
            json = new JSONObject(message);
            action = json.optString("action");
        } catch (Exception e) {
            return;
        }

        switch (action) {
            case "game_request":
                if (!appInForeground) show(context, json);
                break;
            case "request_canceled":
            case "request_expired":
            case "game_started": {
                String requestId = json.optString("request_id", null);
                if ("game_started".equals(action) || requestId == null || requestId.equals(shownRequestId)) {
                    cancel(context);
                }
                break;
            }
            default:
                break;
        }
    }

    static void cancel(Context context) {
        shownRequestId = null;
        manager(context).cancel(NOTIFICATION_ID);
    }

    private static void show(Context context, JSONObject request) {
        String requestId = request.optString("request_id", null);
        if (requestId == null) return;
        String from = request.optString("from_name", "A player");
        int amount = request.optInt("amount", 0);
        boolean rematch = request.optBoolean("rematch", false);

        NotificationManager manager = manager(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Challenges", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("When another player challenges you while Piga21 is in the background");
            manager.createNotificationChannel(channel);
        }

        // Opens the app on its existing screen, which then accepts and moves to the table
        Intent open = new Intent(context, AndroidLauncher.class)
            .putExtra(EXTRA_ACCEPT_REQUEST, requestId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, requestId.hashCode(), open,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(context, CHANNEL_ID)
            : new Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH);
        Notification notification = builder
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(rematch ? from + " wants a rematch!" : from + " challenges you!")
            .setContentText(String.format(Locale.US, "Stake %,d points. Tap to play now.", amount))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build();

        shownRequestId = requestId;
        manager.notify(NOTIFICATION_ID, notification);
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
