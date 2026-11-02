package com.piga21.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.HttpRequestBuilder;
import com.badlogic.gdx.net.HttpStatus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.piga21.Piga21Game;

/**
 * The signed-in player's profile on the server: reading it and changing the nickname.
 */
public class PlayerApi {
    private static final int TIMEOUT_MS = 10000;

    public interface Callback {
        void onSuccess(JsonObject player);

        void onFailure(String message);
    }

    /**
     * The signed-in player's own profile (includes their email).
     */
    public static void fetchProfile(Callback callback) {
        request(Net.HttpMethods.GET, "/player/me", null, callback);
    }

    public static void updateNickName(String nickName, Callback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("nick_name", nickName);
        request(Net.HttpMethods.PATCH, "/player/me", body.toString(), callback);
    }

    private static void request(String method, String path, String body, Callback callback) {
        String token = Util.getApiToken();
        if (token.isEmpty()) {
            if (callback != null) callback.onFailure("Not signed in");
            return;
        }

        HttpRequestBuilder builder = new HttpRequestBuilder()
            .newRequest()
            .method(method)
            .url(Constants.API_BASE_URL + path)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .timeout(TIMEOUT_MS);
        if (body != null) {
            builder.header("Content-Type", "application/json").content(body);
        }

        Gdx.net.sendHttpRequest(builder.build(), new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                int status = httpResponse.getStatus().getStatusCode();
                String text = httpResponse.getResultAsString();

                Gdx.app.postRunnable(() -> {
                    if (status >= HttpStatus.SC_OK && status < HttpStatus.SC_MULTIPLE_CHOICES) {
                        JsonObject player = JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("data");
                        if (callback != null) callback.onSuccess(player);
                    } else if (status == HttpStatus.SC_UNAUTHORIZED) {
                        // Saved login no longer valid: get a fresh one
                        if (Gdx.app.getApplicationListener() instanceof Piga21Game game && game.getAuthInterface() != null) {
                            game.getAuthInterface().onSessionRejected();
                        }
                        if (callback != null) callback.onFailure("error:Signing you in again");
                    } else if (callback != null) {
                        // 4xx: the server understood and said no (e.g. a nickname that's taken)
                        String prefix = status >= 400 && status < 500 ? "rejected:" : "error:";
                        callback.onFailure(prefix + errorMessage(text));
                    }
                });
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.error("PlayerApi", method + " " + path + " failed", t);
                if (callback != null) Gdx.app.postRunnable(() -> callback.onFailure("offline:" + t.getMessage()));
            }

            @Override
            public void cancelled() {
                if (callback != null) Gdx.app.postRunnable(() -> callback.onFailure("offline:cancelled"));
            }
        });
    }

    /**
     * The human-readable part of a failure passed to {@link Callback#onFailure}.
     */
    public static String readableMessage(String failure) {
        int colon = failure.indexOf(':');
        return colon >= 0 ? failure.substring(colon + 1) : failure;
    }

    private static String errorMessage(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject()
                .getAsJsonObject("meta").get("message").getAsString();
        } catch (Exception e) {
            return "Request failed";
        }
    }
}
