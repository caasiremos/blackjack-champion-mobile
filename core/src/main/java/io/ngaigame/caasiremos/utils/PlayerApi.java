package io.ngaigame.caasiremos.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.net.HttpRequestBuilder;
import com.badlogic.gdx.net.HttpStatus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.ngaigame.caasiremos.MainBlackJack;
import io.ngaigame.caasiremos.points.GamePoint;

/**
 * The player's chips live on the server. This talks to the wallet endpoints and keeps the
 * local copy in {@link GamePoint} in step with the server's balance.
 *
 * Classic rounds are queued and sent in order, so a round played without signal is applied
 * once the phone is back online instead of being lost.
 */
public class PlayerApi {
    private static final String PENDING_ROUNDS = "pending_classic_rounds";
    private static final int TIMEOUT_MS = 10000;

    private static boolean flushing = false;

    public interface Callback {
        void onSuccess(JsonObject player);

        void onFailure(String message);
    }

    /**
     * Send any queued classic rounds, then load the server balance into GamePoint.
     */
    public static void syncBalance(Callback callback) {
        flushPendingRounds(() -> request(Net.HttpMethods.GET, "/player/me", null, callback));
    }

    /**
     * Report a finished classic round. The local balance already reflects it; the server
     * confirms it (or corrects it) when the round is applied.
     *
     * @param bet total chips wagered this round, including a double
     * @param net chips won (positive) or lost (negative) this round
     */
    public static void reportClassicRound(int bet, int net) {
        JsonObject round = new JsonObject();
        round.addProperty("bet", bet);
        round.addProperty("net", net);

        JsonArray pending = pendingRounds();
        pending.add(round);
        savePendingRounds(pending);

        flushPendingRounds(null);
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

    /**
     * Socket messages that carry the player's profile ("authenticated", "balance_updated",
     * "profile", "leaderboard") update the local balance through here.
     */
    public static void applyPlayerUpdate(JsonObject message) {
        JsonObject player = null;
        if (message.has("player") && message.get("player").isJsonObject()) {
            player = message.getAsJsonObject("player");
        } else if (message.has("me") && message.get("me").isJsonObject()) {
            player = message.getAsJsonObject("me");
        }

        if (player != null && player.has("balance") && pendingRounds().isEmpty()) {
            new GamePoint().storePoints(player.get("balance").getAsInt());
        }
    }

    private static void flushPendingRounds(Runnable onDone) {
        JsonArray pending = pendingRounds();
        if (flushing || pending.isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }

        flushing = true;
        JsonObject round = pending.get(0).getAsJsonObject();
        request(Net.HttpMethods.POST, "/player/classic-round", round.toString(), new Callback() {
            @Override
            public void onSuccess(JsonObject player) {
                dropFirstPendingRound();
                flushing = false;
                if (pendingRounds().isEmpty() && player != null) {
                    new GamePoint().storePoints(player.get("balance").getAsInt());
                }
                flushPendingRounds(onDone);
            }

            @Override
            public void onFailure(String message) {
                flushing = false;
                if (message.startsWith("rejected:")) {
                    // The server refused this round, so retrying won't help; the next sync corrects the balance
                    dropFirstPendingRound();
                    flushPendingRounds(onDone);
                } else if (onDone != null) {
                    onDone.run();
                }
            }
        });
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
                        if (player != null && player.has("balance") && pendingRounds().isEmpty()) {
                            new GamePoint().storePoints(player.get("balance").getAsInt());
                        }
                        if (callback != null) callback.onSuccess(player);
                    } else if (status == HttpStatus.SC_UNAUTHORIZED) {
                        // Saved login no longer valid: get a fresh one
                        if (Gdx.app.getApplicationListener() instanceof MainBlackJack game && game.getAuthInterface() != null) {
                            game.getAuthInterface().onSessionRejected();
                        }
                        if (callback != null) callback.onFailure("error:Signing you in again");
                    } else if (callback != null) {
                        // 4xx: the server understood and said no (cooldown, invalid round, ...)
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

    private static Preferences prefs() {
        return Gdx.app.getPreferences(GamePoint.GAME_PREFS);
    }

    private static JsonArray pendingRounds() {
        try {
            return JsonParser.parseString(prefs().getString(PENDING_ROUNDS, "[]")).getAsJsonArray();
        } catch (Exception e) {
            return new JsonArray();
        }
    }

    private static void savePendingRounds(JsonArray rounds) {
        prefs().putString(PENDING_ROUNDS, rounds.toString());
        prefs().flush();
    }

    private static void dropFirstPendingRound() {
        JsonArray pending = pendingRounds();
        if (!pending.isEmpty()) {
            pending.remove(0);
            savePendingRounds(pending);
        }
    }
}
