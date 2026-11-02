package com.piga21.wallet;

import com.badlogic.gdx.utils.Timer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.piga21.sockets.WebSocketHandler;

/**
 * The player's cash wallet (real UGX, separate from play chips), kept in step with the server
 * over the game socket. Balances and statuses shown in the app only ever come from here, and
 * only from what the server sent: nothing is credited on the phone.
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
    private WebSocketHandler socket;

    private boolean loaded;
    private long balance, reserved;
    private boolean withdrawalsEnabled = true;
    private long minAmount = 1000, maxAmount = 5000000, depositFee, withdrawalFee, dailyWithdrawalLimit;
    private final List<WalletTransaction> transactions = new ArrayList<>();
    private boolean hasMoreHistory;
    private int historyPage;

    // The request awaiting the server's acknowledgement, kept so it can be resent unchanged
    private String pendingRequestId;
    private Map<String, Object> pendingRequest;
    private Timer.Task ackTimeout;

    public static WalletClient get() {
        if (instance == null) instance = new WalletClient();
        return instance;
    }

    public void attach(WebSocketHandler socket) {
        this.socket = socket;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    /**
     * Try to reconnect after the socket gave up. The server re-authenticates and we refresh.
     */
    public void reconnect() {
        if (socket != null && !socket.isConnected()) socket.connect();
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
        Map<String, Object> data = new HashMap<>();
        data.put("action", "wallet_summary");
        send(data);
    }

    public void loadMoreHistory() {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "wallet_history");
        data.put("page", historyPage + 1);
        send(data);
    }

    /**
     * @return the request ID, used to match the server's answer
     */
    public String deposit(long amount, String phoneNumber) {
        return start("wallet_deposit", amount, phoneNumber);
    }

    public String withdraw(long amount, String phoneNumber) {
        return start("wallet_withdraw", amount, phoneNumber);
    }

    /**
     * Send the waiting request again, unchanged. Safe: the server deduplicates by request ID.
     */
    public void retryPending() {
        if (pendingRequest != null) {
            send(pendingRequest);
            scheduleAckTimeout(pendingRequestId);
        }
    }

    public boolean isWaitingFor(String requestId) {
        return requestId != null && requestId.equals(pendingRequestId);
    }

    private String start(String action, long amount, String phoneNumber) {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("action", action);
        data.put("request_id", requestId);
        data.put("amount", amount);
        data.put("phone_number", phoneNumber);

        pendingRequestId = requestId;
        pendingRequest = data;
        send(data);
        scheduleAckTimeout(requestId);
        return requestId;
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
            if (ackTimeout != null) ackTimeout.cancel();
        }
    }

    private void send(Map<String, Object> data) {
        if (isConnected()) socket.sendMessage(gson.toJson(data));
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
            case "wallet_summary":
                applyWallet(message.getAsJsonObject("wallet"));
                applyLimits(message.getAsJsonObject("limits"));
                transactions.clear();
                historyPage = 0;
                addTransactions(message.getAsJsonArray("transactions"));
                loaded = true;
                notifyWalletChanged();
                break;
            case "wallet_history":
                int page = message.has("page") ? message.get("page").getAsInt() : 0;
                if (page == historyPage + 1) {
                    historyPage = page;
                    addTransactions(message.getAsJsonArray("transactions"));
                    notifyWalletChanged();
                }
                break;
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
            case "wallet_transaction_updated": {
                WalletTransaction transaction = new WalletTransaction(message.getAsJsonObject("transaction"));
                applyWallet(message.getAsJsonObject("wallet"));
                upsert(transaction);
                for (Listener listener : new ArrayList<>(listeners)) listener.onTransactionUpdated(transaction);
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
        hasMoreHistory = list.size() >= 20;
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
    public long getReserved() { return reserved; }
    public boolean areWithdrawalsEnabled() { return withdrawalsEnabled; }
    public long getMinAmount() { return minAmount; }
    public long getMaxAmount() { return maxAmount; }
    public long getDepositFee() { return depositFee; }
    public long getWithdrawalFee() { return withdrawalFee; }
    public long getDailyWithdrawalLimit() { return dailyWithdrawalLimit; }
    public List<WalletTransaction> getTransactions() { return transactions; }
    public boolean hasMoreHistory() { return hasMoreHistory; }

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
