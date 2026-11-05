package com.piga21.temsoft;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONException;
import org.json.JSONObject;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import com.piga21.Piga21Game;
import com.piga21.points.GamePoint;


public class AndroidLauncher extends AndroidApplication{
    private static final String ASKED_FOR_NOTIFICATIONS = "asked_for_notifications";
    private GoogleSignInHandler googleSignInHandler;
    private AndroidWebSocketHandler androidWebSocketClient;
    private AndroidCommonUtilInterface androidToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;


        androidWebSocketClient = new AndroidWebSocketHandler(this);
        androidToast = new AndroidCommonUtilInterface(this);
        googleSignInHandler = new GoogleSignInHandler(this, androidWebSocketClient);
        androidWebSocketClient.setSessionRejectedListener(googleSignInHandler::onSessionRejected);

        // Keeps trying to sign the player in until they have an account; the first try happens in onResume
        googleSignInHandler.start();

        // Create layout
        FrameLayout layout = new FrameLayout(this);


        // Initialize game view and add to layout
        View gameView = initializeForView(new Piga21Game(androidWebSocketClient, googleSignInHandler, androidToast), configuration);
        layout.addView(gameView);

        // Set content view
        setContentView(layout);

        acceptChallengeFrom(getIntent());
    }

    /**
     * Opened from a challenge notification: accept it. The server answers with game_started,
     * which takes whichever screen is showing to the game table.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptChallengeFrom(intent);
    }

    private void acceptChallengeFrom(Intent intent) {
        String requestId = intent == null ? null : intent.getStringExtra(ChallengeNotifier.EXTRA_ACCEPT_REQUEST);
        if (requestId == null) return;
        intent.removeExtra(ChallengeNotifier.EXTRA_ACCEPT_REQUEST);
        ChallengeNotifier.cancel(this);
        try {
            JSONObject accept = new JSONObject();
            accept.put("action", "accept_request");
            accept.put("request_id", requestId);
            androidWebSocketClient.sendWhenSignedIn(accept.toString());
        } catch (JSONException e) {
            // Can't happen with these values
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Stay online while in the background, until the app is closed
        PresenceService.onAppClosed = androidWebSocketClient::close;
        PresenceService.start(this);
        askToShowOnlineNotification();
    }

    @Override
    protected void onDestroy() {
        // Backed out of (or otherwise finished): go offline
        if (isFinishing()) {
            androidWebSocketClient.close();
            PresenceService.stop(this);
        }
        super.onDestroy();
    }

    /**
     * Android 13+ asks before an app may show notifications; without it the player is still kept
     * online, there's just no notification saying so.
     */
    private void askToShowOnlineNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences prefs = getSharedPreferences(GamePoint.GAME_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(ASKED_FOR_NOTIFICATIONS, false)) return;
        prefs.edit().putBoolean(ASKED_FOR_NOTIFICATIONS, true).apply();
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }

    @Override
    protected void onPause() {
        ChallengeNotifier.appInForeground = false;
        googleSignInHandler.onAppPaused();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ChallengeNotifier.appInForeground = true;
        googleSignInHandler.onAppResumed();
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            NetworkCapabilities networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork);

            return networkCapabilities != null &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        return false;
    }
}
