package com.piga21.temsoft;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.piga21.utils.CommonUtilInterface;


public class AndroidCommonUtilInterface implements CommonUtilInterface {
    private final Activity activity;

    public AndroidCommonUtilInterface(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void showToast(final String message) {
        activity.runOnUiThread(() ->
            android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void showLongToast(final String message) {
        activity.runOnUiThread(() ->
            android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show()
        );
    }

    @Override
    public void shareApp(String text, String subject) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);

        // Add play store link if you want
        String packageName = activity.getPackageName();
        String playStoreLink = "https://play.google.com/store/apps/details?id=" + packageName;
        String fullText = text + "\n\n" + playStoreLink;
        shareIntent.putExtra(Intent.EXTRA_TEXT, fullText);

        activity.startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    @Override
    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);

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
