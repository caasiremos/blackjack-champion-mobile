package com.piga21.temsoft;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.piga21.R;

/**
 * Keeps Piga21 connected while it's in the background, so the player stays on other players'
 * online list and can still be challenged. Without it Android cuts a backgrounded app's network
 * within seconds. It shows a quiet notification while running, and ends when the app is closed
 * (back out of it, or swipe it away from recent apps), which also closes the connection.
 */
public class PresenceService extends Service {
    private static final String TAG = "PresenceService";
    private static final String CHANNEL_ID = "presence";
    private static final int NOTIFICATION_ID = 21;

    /** What to do when the player swipes the app away: close the connection so they go offline */
    static Runnable onAppClosed;

    /**
     * Only call while the app is on screen: Android doesn't let a backgrounded app start one.
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, PresenceService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            // Refused (e.g. started too late): the app still works, it just goes offline in the background
            Log.w(TAG, "Couldn't keep the app online in the background", e);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, PresenceService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        // If Android ends the app, it shouldn't come back by itself: the player opens it again
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (onAppClosed != null) onAppClosed.run();
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Online status", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shown while you're online for challenges");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(this, AndroidLauncher.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent tap = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        return builder
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle("You're online on Piga21")
            .setContentText("Other players can challenge you. Close the app to go offline.")
            .setContentIntent(tap)
            .setOngoing(true)
            .setShowWhen(false)
            .build();
    }
}
