package io.ngaigame.caasiremos.android;


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import io.ngaigame.caasiremos.MainBlackJack;


public class AndroidLauncher extends AndroidApplication{
    private AndroidAdHandler androidAdHandler;
    private GoogleSignInHandler googleSignInHandler;
    private AndroidWebSocketHandler androidWebSocketClient;
    private AndroidCommonUtilInterface androidToast;
    AdView adView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;


        // Initialize AdMob
        MobileAds.initialize(this, initializationStatus -> {});

        // Create the AndroidAdHandler
        androidAdHandler = new AndroidAdHandler(this);
        androidWebSocketClient = new AndroidWebSocketHandler(this);
        androidToast = new AndroidCommonUtilInterface(this);
        googleSignInHandler = new GoogleSignInHandler(this, androidWebSocketClient);
        androidWebSocketClient.setSessionRejectedListener(googleSignInHandler::onSessionRejected);

        // Keeps trying to sign the player in until they have an account; the first try happens in onResume
        googleSignInHandler.start();

        // Create layout
        FrameLayout layout = new FrameLayout(this);


        // Initialize game view and add to layout
        View gameView = initializeForView(new MainBlackJack(androidAdHandler, androidWebSocketClient, googleSignInHandler, androidToast), configuration);
        layout.addView(gameView);

        // Set content view
        setContentView(layout);


        // Load ads (banner, interstitial, rewarded)
        androidAdHandler.setRootLayout(layout); // Pass the layout to adHandler if needed
        androidAdHandler.loadBannerAd(); // Now it can add the ad to the layout
        androidAdHandler.loadInterstitialAd();
        androidAdHandler.loadRewardedAd();

    }

    @Override
    protected void onPause() {
        if (adView != null) {
            adView.pause();
        }
        googleSignInHandler.onAppPaused();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adView != null) {
            adView.resume();
        }
        googleSignInHandler.onAppResumed();
    }

    @Override
    protected void onDestroy() {
        if (adView != null) {
            adView.destroy();
        }
        super.onDestroy();
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
