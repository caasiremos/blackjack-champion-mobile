package com.piga21.temsoft;


import static com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.Executors;

import com.piga21.R;
import com.piga21.auth.GoogleSignInInterface;
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
 * Signs the player in with Google, then creates/fetches their account on our server (the API
 * token is what the game actually needs). Until that works it keeps trying:
 * - on app start, every return to the foreground, and whenever the network comes back
 * - after network/server errors, with a growing delay
 * - after the player closes the Google picker, again a few minutes later (never in a tight loop)
 * - straight away when the player taps something that needs an account
 */
public class GoogleSignInHandler implements GoogleSignInInterface {
    private final AndroidLauncher activity;
    private static final String TAG = "GoogleSignIn";
    public FirebaseAuth mAuth;
    private CredentialManager credentialManager;
    AndroidWebSocketHandler androidWebSocketHandler;

    private static final long MIN_RETRY_MS = 5_000;
    private static final long MAX_RETRY_MS = 5 * 60_000;
    private static final long FIRST_REPROMPT_MS = 2 * 60_000;
    private static final long MAX_REPROMPT_MS = 10 * 60_000;

    // All sign-in state is touched on the main thread only
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable retry = () -> ensureSignedIn(false);
    private boolean inProgress = false;
    private boolean attemptUserAsked = false; // the current attempt came from the player tapping something
    private boolean foreground = false;
    private long retryDelayMs = MIN_RETRY_MS;
    private long repromptDelayMs = FIRST_REPROMPT_MS;
    private long dismissedUntil = 0; // after the player closes the picker, don't show it again before this

    public GoogleSignInHandler(AndroidLauncher activity, AndroidWebSocketHandler androidWebSocketHandler) {
        this.activity = activity;
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Initialize Credential Manager
        credentialManager = CredentialManager.create(activity);
        this.androidWebSocketHandler = androidWebSocketHandler;
    }

    /**
     * Call once from onCreate: retries sign-in whenever the phone gets a connection.
     */
    public void start() {
        ConnectivityManager connectivity = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    ensureSignedIn(false);
                }
            });
        }
    }

    public void onAppResumed() {
        mainHandler.post(() -> {
            foreground = true;
            ensureSignedIn(false);
        });
    }

    public void onAppPaused() {
        mainHandler.post(() -> {
            foreground = false;
            // The Google picker needs the app on screen; resuming tries again
            mainHandler.removeCallbacks(retry);
        });
    }

    /**
     * Make sure the player ends up with a server account, doing only the steps still missing.
     *
     * @param userAsked the player tapped something that needs an account, so show the picker even if
     *                  they closed it recently
     */
    public void ensureSignedIn(boolean userAsked) {
        mainHandler.post(() -> {
            if (hasApiToken()) {
                androidWebSocketHandler.connect(); // no-op if already connected
                return;
            }
            if (inProgress || !foreground) return;
            if (!AndroidLauncher.isNetworkAvailable(activity)) return; // the network callback tries again
            long waitMs = dismissedUntil - System.currentTimeMillis();
            if (!userAsked && waitMs > 0) {
                // Recently dismissed: ask again when the wait is over, not before
                mainHandler.removeCallbacks(retry);
                mainHandler.postDelayed(retry, waitMs);
                return;
            }

            mainHandler.removeCallbacks(retry);
            inProgress = true;
            attemptUserAsked = userAsked;

            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                // Google part already done; only our server account is missing
                sendUserDataToServer(user);
            } else {
                launchCredentialManager();
            }
        });
    }

    /**
     * The server no longer accepts our saved token (e.g. the account was reset): sign in again.
     */
    @Override
    public void onSessionRejected() {
        mainHandler.post(() -> {
            Log.w(TAG, "Server rejected the saved token, signing in again");
            activity.getSharedPreferences(GamePoint.GAME_PREFS, Context.MODE_PRIVATE).edit()
                .remove(GamePoint.API_TOKEN)
                .apply();
            ensureSignedIn(false);
        });
    }

    @Override
    public void signIn() {
        ensureSignedIn(true);
    }

    /**
     * Signed in means we hold a server token; a Google account alone isn't enough to play online.
     */
    @Override
    public boolean isSignedIn() {
        return hasApiToken();
    }

    private boolean hasApiToken() {
        SharedPreferences prefs = activity.getSharedPreferences(GamePoint.GAME_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(GamePoint.API_TOKEN, null) != null;
    }

    /**
     * Network, Firebase or server trouble: try again later, waiting longer each time.
     */
    private void retryLater(String why) {
        mainHandler.post(() -> {
            inProgress = false;
            Log.w(TAG, "Sign-in didn't finish (" + why + "), retrying in " + retryDelayMs / 1000 + "s");
            mainHandler.removeCallbacks(retry);
            if (foreground) {
                mainHandler.postDelayed(retry, retryDelayMs);
            }
            retryDelayMs = Math.min(retryDelayMs * 2, MAX_RETRY_MS);
        });
    }

    /**
     * The player closed the Google picker (or has no Google account): ask again in a few minutes.
     */
    private void askAgainLater() {
        mainHandler.post(() -> {
            inProgress = false;
            dismissedUntil = System.currentTimeMillis() + repromptDelayMs;
            mainHandler.removeCallbacks(retry);
            if (foreground) {
                mainHandler.postDelayed(retry, repromptDelayMs);
            }
            repromptDelayMs = Math.min(repromptDelayMs * 2, MAX_REPROMPT_MS);
        });
    }

    private void signedIn() {
        mainHandler.post(() -> {
            inProgress = false;
            retryDelayMs = MIN_RETRY_MS;
            repromptDelayMs = FIRST_REPROMPT_MS;
            dismissedUntil = 0;
            mainHandler.removeCallbacks(retry);
            androidWebSocketHandler.connect();
        });
    }

    @Override
    public String getDisplayName() {
        FirebaseUser user = mAuth.getCurrentUser();
        return user != null ? user.getDisplayName() : null;
    }

    @Override
    public String getEmail() {
        return "";
    }

    @Override
    public String getUserId() {
        FirebaseUser user = mAuth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void signOut() {
        mAuth.signOut();
        ClearCredentialStateRequest clearRequest = new ClearCredentialStateRequest();
        credentialManager.clearCredentialStateAsync(
            clearRequest,
            new CancellationSignal(),
            Executors.newSingleThreadExecutor(),
            new CredentialManagerCallback<>() {
                @Override
                public void onResult(@NonNull Void result) {

                }

                @Override
                public void onError(@NonNull ClearCredentialException e) {
                    Log.e(TAG, "Couldn't clear user credentials: " + e.getLocalizedMessage());
                }
            });
    }

    private void launchCredentialManager() {
        try {
            GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(activity.getString(R.string.server_client_id))
                .setNonce(generateRandomNonce()) // Important for security
                .build();

            GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

            credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleSignIn(result.getCredential());
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.e(TAG, "Sign-in failed: " + e.getMessage(), e);

                        if (e instanceof GetCredentialCancellationException) {
                            showToast("Sign in with Google to play online and save your chips");
                            askAgainLater();
                        } else if (e instanceof NoCredentialException) {
                            // No Google account on the phone. If the player asked, take them to add one;
                            // coming back to the game (onResume) tries again.
                            showToast("Add a Google account to this phone to play online");
                            if (attemptUserAsked) {
                                openAddGoogleAccount();
                                // No cooldown: returning from the add-account screen should try at once
                                mainHandler.post(() -> inProgress = false);
                            } else {
                                askAgainLater();
                            }
                        } else {
                            if (e.getMessage() != null && e.getMessage().contains("28404")) {
                                Log.w(TAG, "ID token retrieval failed - check configuration");
                            }
                            retryLater(e.getClass().getSimpleName());
                        }
                    }
                }
            );
        } catch (Exception e) {
            Log.e(TAG, "Credential manager launch failed", e);
            retryLater("launch failed");
        }
    }

    // Generate a secure random nonce for the request
    private String generateRandomNonce() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        return Base64.encodeToString(nonce, Base64.URL_SAFE | Base64.NO_WRAP);
    }

    private void handleSignIn(Credential credential) {
        if (credential instanceof CustomCredential customCredential
            && credential.getType().equals(TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
            Bundle credentialData = customCredential.getData();
            GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credentialData);
            firebaseAuthWithGoogle(googleIdTokenCredential.getIdToken());
        } else {
            Log.w(TAG, "Credential is not of type Google ID!");
            retryLater("unexpected credential type");
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(activity, task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "signInWithCredential:success");
                    FirebaseUser user = mAuth.getCurrentUser();
                    Log.d(TAG, "signInWithCredential:success" + user.getDisplayName());
                    sendUserDataToServer(user);

                    showToast("Welcome: " + user.getDisplayName());
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.getException());
                    retryLater("firebase");
                }
            });
    }

    private void sendUserDataToServer(FirebaseUser user) {
        if (user == null) {
            retryLater("no firebase user");
            return;
        }

        // Create OkHttp client
        OkHttpClient client = new OkHttpClient();

        // Create JSON request body
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("email", user.getEmail());
            jsonBody.put("name", user.getDisplayName());
            // Add more fields if needed
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON body", e);
            retryLater("request body");
            return;
        }

        // Build the request
        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
            .url("https://admin.theagrotracker.com/api/player-account")
            .post(body)
            .addHeader("Content-Type", "application/json")
            //.addHeader("Authorization", "Bearer YOUR_ACCESS_TOKEN")
            .build();

        // Execute the request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to send user data: " + e.getMessage());
                retryLater("server unreachable");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Server returned error: " + response.code());
                    retryLater("server " + response.code());
                    return;
                }
                try {
                    // Parse the JSON response
                    String responseData = response.body().string();
                    JSONObject json = new JSONObject(responseData);

                    // Get the "data" object
                    JSONObject dataObject = json.getJSONObject("data");

                    // Extract the access token
                    String token = dataObject.getString("access_token");
                    String tokenType = dataObject.getString("token_type");
                    Log.d(TAG, "Signed in to the server");

                    // Extract player data
                    JSONObject playerObject = dataObject.getJSONObject("player");
                    int playerId = playerObject.getInt("id");
                    String playerName = playerObject.getString("name");

                    // Store data in SharedPreferences
                    SharedPreferences.Editor editor = activity.getSharedPreferences(GamePoint.GAME_PREFS, Context.MODE_PRIVATE).edit();
                    editor.putString(GamePoint.API_TOKEN, token);
                    editor.putInt(Constants.PLAYER_ID, playerId);
                    editor.putString(Constants.PLAYER_NAME, playerName);
                    // Chips come from the server; the local value is just a copy
                    editor.putInt(GamePoint.GAME_POINTS, playerObject.getInt("balance"));
                    editor.apply();

                    Log.d(TAG, "Player data extracted - ID: " + playerId + ", Name: " + playerName);
                    signedIn();

                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse JSON response", e);
                    retryLater("bad server response");
                }
            }
        });
    }

    private void openAddGoogleAccount() {
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT)
                    .putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[]{"com.google"})
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "No add-account screen on this device", e);
            }
        });
    }

    private void showToast(String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }
}
