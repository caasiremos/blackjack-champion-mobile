package com.piga21.temsoft;


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import com.piga21.Piga21Game;


public class AndroidLauncher extends AndroidApplication{
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
    }

    @Override
    protected void onPause() {
        googleSignInHandler.onAppPaused();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
