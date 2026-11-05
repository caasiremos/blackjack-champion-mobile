package com.piga21.temsoft;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.IOException;

import com.piga21.points.GamePoint;
import com.piga21.utils.Constants;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Tells the server this phone's push token, so it can reach the app when a top up or redemption
 * finishes. Done once signed in, and again whenever Firebase gives the phone a new token.
 */
public class PushRegistration {
    private static final String TAG = "PushRegistration";
    private static final OkHttpClient client = new OkHttpClient();

    /**
     * Ask Firebase for the current token and register it (when signed in).
     */
    public static void registerCurrent(Context context) {
        try {
            FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> register(context, token))
                .addOnFailureListener(e -> Log.w(TAG, "No push token: " + e.getMessage()));
        } catch (Exception e) {
            Log.w(TAG, "Push unavailable", e);
        }
    }

    public static void register(Context context, String pushToken) {
        SharedPreferences prefs = context.getSharedPreferences(GamePoint.GAME_PREFS, Context.MODE_PRIVATE);
        String apiToken = prefs.getString(GamePoint.API_TOKEN, null);
        if (apiToken == null || pushToken == null) return; // registered after sign-in instead

        try {
            JSONObject body = new JSONObject().put("push_token", pushToken);
            Request request = new Request.Builder()
                .url(Constants.API_BASE_URL + "/player/device")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiToken)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8")))
                .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "Couldn't register for pushes: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) Log.w(TAG, "Push registration refused: " + response.code());
                    response.close();
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Couldn't register for pushes", e);
        }
    }
}
