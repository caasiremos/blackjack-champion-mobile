package com.piga21.temsoft;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.badlogic.gdx.Gdx;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Locale;
import java.util.Map;

import com.piga21.R;
import com.piga21.wallet.WalletClient;

/**
 * Pushes from the server (Firebase). When a top up or redemption changes status, the server
 * sends its reference and status here; the app then fetches the points over HTTP. If the app
 * isn't on screen, the player also gets a notification when it finishes.
 */
public class PushService extends FirebaseMessagingService {
    private static final String TAG = "PushService";
    private static final String CHANNEL_ID = "wallet";
    private static final int NOTIFICATION_ID = 23;

    @Override
    public void onNewToken(@NonNull String token) {
        PushRegistration.register(this, token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        if (!"wallet".equals(data.get("type"))) return;

        Log.d(TAG, "Wallet push: " + data.get("kind") + " " + data.get("status"));
        // The game is running: fetch that transaction (its screen updates) and the points now
        if (Gdx.app != null) {
            String reference = data.get("reference");
            Gdx.app.postRunnable(() -> WalletClient.get().refreshTransaction(reference));
        }
        if (!ChallengeNotifier.appInForeground) {
            showFinished(data);
        }
    }

    private void showFinished(Map<String, String> data) {
        String status = data.get("status");
        boolean successful = "successful".equals(status);
        if (!successful && !"failed".equals(status)) return; // only the end result is worth a notification

        boolean deposit = "deposit".equals(data.get("kind"));
        long amount = 0;
        try {
            amount = Long.parseLong(data.get("amount"));
        } catch (Exception ignored) {
            // shown without the amount
        }
        String what = (deposit ? "Top up" : "Redemption") + (amount > 0 ? String.format(Locale.US, " of %,d points", amount) : "");
        String title = what + (successful ? " successful" : " failed");
        String text = successful
            ? (deposit ? "Your points are ready to play." : "The money is on its way to your phone.")
            : (deposit ? "You were not charged." : "Your points are back.");

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Top ups and redemptions", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("When a top up or redemption finishes");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, AndroidLauncher.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tap = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this).setPriority(Notification.PRIORITY_HIGH);
        manager.notify(NOTIFICATION_ID, builder
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build());
    }
}
