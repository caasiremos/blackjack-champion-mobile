package com.piga21.wallet;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.HttpRequestBuilder;
import com.badlogic.gdx.net.HttpStatus;
import com.badlogic.gdx.utils.Timer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.piga21.Piga21Game;
import com.piga21.utils.Constants;
import com.piga21.utils.Util;

/**
 * The player's points wallet (real money, topped up and redeemed by mobile money), kept in step
 * with the server over HTTP only: no socket and no queue. When Temsoft reports a result the server
 * sends the phone a push, and the app answers it by fetching the points again ({@link #refresh}).
 * Balances and statuses shown in the app only ever come from here, and only from what the server
 * sent: nothing is credited on the phone.
 *
 * Each confirmed deposit/withdrawal gets one request ID. Sending it again (a retry, or after a
 * reconnect) can't create a second transaction: the server answers with the original.
 */
public class WalletClient {
    // Shape of wallet messages this app understands (the server sends wallet_version)
    public static final int PROTOCOL_VERSION = 1;
    // No acknowledgement after this long: offer to resend the same request
    public static final float ACK_TIMEOUT_SECONDS = 15f;

    public interface Listener {
        /** Balance, limits or the transaction list changed */
        default void onWalletChanged() {}

        /** The server accepted a request (or recognised a repeat) and created this transaction */
        default void onRequestAccepted(String requestId, WalletTransaction transaction) {}

        /** The server refused a request; message is safe to show */
        default void onRequestFailed(String requestId, String code, String message) {}

        /** A transaction's status changed */
        default void onTransactionUpdated(WalletTransaction transaction) {}

        /** No answer to a request in time */
        default void onRequestTimedOut(String requestId) {}
    }

    private static WalletClient instance;

    private final Gson gson = new Gson();
    private final List<Listener> listeners = new ArrayList<>();
    private boolean reachable = true; // the last request got an answer from the server

    private boolean loaded;
    private long balance, reserved;
    private boolean withdrawalsEnabled = true;
    private String savedPhone; // the player's own mobile money number, e.g. 0772123456, or null
    private long minAmount = 1000, maxAmount = 5000000, depositFee, withdrawalFee, dailyWithdrawalLimit;
    private final List<WalletTransaction> transactions = new ArrayList<>();

    // The request awaiting the server's acknowledgement, kept so it can be resent unchanged
    private String pendingRequestId;
    private Map<String, Object> pendingRequest;
    private String pendingPath;
    private Timer.Task ackTimeout;

    public static WalletClient get() {
        if (instance == null) instance = new WalletClient();
        return instance;
    }

    /**
     * Whether the server answered the last time we asked (false while the phone is offline).
     */
    public boolean isConnected() {
        return reachable;
    }

    /**
     * Ask again after being offline.
     */
    public void reconnect() {
        refresh();
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /* Requests */

    /**
     * Ask for the authoritative balance, limits and recent transactions.
     */
    public void refresh() {
        call(Net.HttpMethods.GET, "/wallet", null);
    }

    /**
     * One transaction's latest state, e.g. when the server's push says it changed.
     */
    public void refreshTransaction(String reference) {
        if (reference == null || !reference.matches("[0-9a-fA-F-]{36}")) {
            refresh();
            return;
        }
        call(Net.HttpMethods.GET, "/wallet/transactions/" + reference, null);
    }

    /**
     * @return the request ID, used to match the server's answer
     */
    public String deposit(long amount, String phoneNumber) {
        return start("deposit", amount, phoneNumber);
    }

    public String withdraw(long amount, String phoneNumber) {
        return start("withdraw", amount, phoneNumber);
    }

    /**
     * Send the waiting request again, unchanged. Safe: the server deduplicates by request ID.
     */
    public void retryPending() {
        if (pendingRequest != null) {
            call(Net.HttpMethods.POST, "/wallet/" + pendingPath, pendingRequest);
            scheduleAckTimeout(pendingRequestId);
        }
    }

    public boolean isWaitingFor(String requestId) {
        return requestId != null && requestId.equals(pendingRequestId);
    }

    /**
     * @param path deposit | withdraw
     */
    private String start(String path, long amount, String phoneNumber) {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("request_id", requestId);
        data.put("amount", amount);
        data.put("phone_number", phoneNumber);

        pendingRequestId = requestId;
        pendingRequest = data;
        pendingPath = path;
        call(Net.HttpMethods.POST, "/wallet/" + path, data);
        scheduleAckTimeout(requestId);
        return requestId;
    }

    /**
     * Every answer comes back in the wallet message shape (wallet_summary, wallet_deposit_ack,
     * wallet_error, ...), so it goes through {@link #handle}. No answer (offline, timeout) leaves
     * a top up or redemption waiting to be resent.
     */
    private void call(String method, String path, Map<String, Object> data) {
        String token = Util.getApiToken();
        if (token.isEmpty()) return;

        HttpRequestBuilder builder = new HttpRequestBuilder()
            .newRequest()
            .method(method)
            .url(Constants.API_BASE_URL + path)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .timeout((int) (ACK_TIMEOUT_SECONDS * 1000));
        if (data != null) {
            builder.header("Content-Type", "application/json").content(gson.toJson(data));
        }
        Net.HttpRequest request = builder.build();

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse response) {
                int status = response.getStatus().getStatusCode();
                String body = response.getResultAsString();
                Gdx.app.postRunnable(() -> {
                    reachable = true;
                    if (status == HttpStatus.SC_UNAUTHORIZED) {
                        // Saved login no longer valid: get a fresh one, then the request can be resent
                        if (Gdx.app.getApplicationListener() instanceof Piga21Game game && game.getAuthInterface() != null) {
                            game.getAuthInterface().onSessionRejected();
                        }
                        return;
                    }
                    try {
                        handle(JsonParser.parseString(body).getAsJsonObject());
                    } catch (Exception e) {
                        // Not our JSON (a proxy error page, say): treat it as no answer
                        Gdx.app.error("WalletClient", "Unexpected answer to " + path + " (" + status + ")");
                    }
                });
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.error("WalletClient", path + " failed: " + t.getMessage());
                Gdx.app.postRunnable(() -> {
                    reachable = false;
                    notifyWalletChanged();
                });
            }

            @Override
            public void cancelled() {
            }
        });
    }

    private void scheduleAckTimeout(String requestId) {
        if (ackTimeout != null) ackTimeout.cancel();
        ackTimeout = Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                if (isWaitingFor(requestId)) {
                    for (Listener listener : new ArrayList<>(listeners)) listener.onRequestTimedOut(requestId);
                }
            }
        }, ACK_TIMEOUT_SECONDS);
    }

    private void clearPending(String requestId) {
        if (requestId != null && requestId.equals(pendingRequestId)) {
            pendingRequestId = null;
            pendingRequest = null;
            pendingPath = null;
            if (ackTimeout != null) ackTimeout.cancel();
        }
    }

    /* Messages from the server */

    /**
     * Call from the screen's socket listener (on the render thread).
     *
     * @return true if the message was the wallet's and needs no further handling
     */
    public boolean handle(JsonObject message) {
        String action = message.has("action") ? message.get("action").getAsString() : "";

        if ("authenticated".equals(action)) {
            // Fresh connection: our view may be stale, and an unanswered request may never have arrived
            refresh();
            retryPending();
            return false;
        }
        if (!action.startsWith("wallet_")) return false;

        switch (action) {
            case "wallet_summary": {
                savedPhone = textOf(message, "phone");
                applyWallet(message.getAsJsonObject("wallet"));
                applyLimits(message.getAsJsonObject("limits"));
                // Anything whose status moved since we last looked: tell whoever is watching it
                List<WalletTransaction> before = new ArrayList<>(transactions);
                transactions.clear();
                addTransactions(message.getAsJsonArray("transactions"));
                loaded = true;
                for (WalletTransaction now : new ArrayList<>(transactions)) {
                    WalletTransaction was = null;
                    for (WalletTransaction old : before) {
                        if (old.reference.equals(now.reference)) was = old;
                    }
                    if (was != null && !java.util.Objects.equals(was.status, now.status)) {
                        for (Listener listener : new ArrayList<>(listeners)) listener.onTransactionUpdated(now);
                    }
                }
                notifyWalletChanged();
                break;
            }
            case "wallet_deposit_ack":
            case "wallet_withdraw_ack": {
                String requestId = textOf(message, "request_id");
                WalletTransaction transaction = new WalletTransaction(message.getAsJsonObject("transaction"));
                clearPending(requestId);
                applyWallet(message.getAsJsonObject("wallet"));
                upsert(transaction);
                for (Listener listener : new ArrayList<>(listeners)) listener.onRequestAccepted(requestId, transaction);
                notifyWalletChanged();
                break;
            }
            case "wallet_transaction":
            case "wallet_transaction_updated":
            // Pushed the moment Temsoft's callback is confirmed, so the result shows at once
            case "wallet_callback": {
                WalletTransaction transaction = new WalletTransaction(message.getAsJsonObject("transaction"));
                // The instant push and the regular one can both bring the same news: tell listeners once
                WalletTransaction known = find(transaction.reference);
                boolean changed = known == null || !java.util.Objects.equals(known.status, transaction.status);
                applyWallet(message.getAsJsonObject("wallet"));
                upsert(transaction);
                if (changed || "wallet_transaction".equals(action)) {
                    for (Listener listener : new ArrayList<>(listeners)) listener.onTransactionUpdated(transaction);
                }
                notifyWalletChanged();
                break;
            }
            case "wallet_error": {
                String requestId = textOf(message, "request_id");
                String code = textOf(message, "code");
                // A server hiccup: keep the request so it can be retried with the same ID
                if (!"server_error".equals(code)) clearPending(requestId);
                for (Listener listener : new ArrayList<>(listeners)) {
                    listener.onRequestFailed(requestId, code, textOf(message, "message"));
                }
                break;
            }
            default:
                break;
        }
        return true;
    }

    private void applyWallet(JsonObject wallet) {
        if (wallet == null) return;
        balance = wallet.get("balance").getAsLong();
        reserved = wallet.get("reserved").getAsLong();
        withdrawalsEnabled = !wallet.has("withdrawals_enabled") || wallet.get("withdrawals_enabled").getAsBoolean();
    }

    private void applyLimits(JsonObject limits) {
        if (limits == null) return;
        minAmount = limits.get("min_amount").getAsLong();
        maxAmount = limits.get("max_amount").getAsLong();
        depositFee = limits.get("deposit_fee").getAsLong();
        withdrawalFee = limits.get("withdrawal_fee").getAsLong();
        dailyWithdrawalLimit = limits.get("daily_withdrawal_limit").getAsLong();
    }

    private void addTransactions(JsonArray list) {
        if (list == null) return;
        for (JsonElement element : list) {
            upsertAtEnd(new WalletTransaction(element.getAsJsonObject()));
        }
    }

    /**
     * Newest first; replaces an older copy of the same transaction.
     */
    private void upsert(WalletTransaction transaction) {
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).reference.equals(transaction.reference)) {
                transactions.set(i, transaction);
                return;
            }
        }
        transactions.add(0, transaction);
    }

    private void upsertAtEnd(WalletTransaction transaction) {
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).reference.equals(transaction.reference)) {
                transactions.set(i, transaction);
                return;
            }
        }
        transactions.add(transaction);
    }

    private void notifyWalletChanged() {
        for (Listener listener : new ArrayList<>(listeners)) listener.onWalletChanged();
    }

    private static String textOf(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /* State */

    public boolean isLoaded() { return loaded; }
    public long getBalance() { return balance; }

    /**
     * The mobile money number saved on the player's profile, or null.
     */
    public String getSavedPhone() { return savedPhone; }

    /**
     * After the player changes it in their profile.
     */
    public void setSavedPhone(String phone) {
        savedPhone = phone == null || phone.isEmpty() ? null : phone;
        notifyWalletChanged();
    }
    public long getReserved() { return reserved; }
    public boolean areWithdrawalsEnabled() { return withdrawalsEnabled; }
    public long getMinAmount() { return minAmount; }
    public long getMaxAmount() { return maxAmount; }
    public long getDepositFee() { return depositFee; }
    public long getWithdrawalFee() { return withdrawalFee; }
    public long getDailyWithdrawalLimit() { return dailyWithdrawalLimit; }
    public List<WalletTransaction> getTransactions() { return transactions; }

    public WalletTransaction find(String reference) {
        for (WalletTransaction transaction : transactions) {
            if (transaction.reference.equals(reference)) return transaction;
        }
        return null;
    }

    public String money(long amount) {
        return String.format(Locale.ENGLISH, "%,d points", amount);
    }

    /* Phone numbers (display only; the server validates and picks the network) */

    /**
     * 0772123456 -> 0772 123 456
     */
    public static String formatPhone(String localNumber) {
        if (localNumber == null || localNumber.length() != 10) return localNumber;
        return localNumber.substring(0, 4) + " " + localNumber.substring(4, 7) + " " + localNumber.substring(7);
    }
}
