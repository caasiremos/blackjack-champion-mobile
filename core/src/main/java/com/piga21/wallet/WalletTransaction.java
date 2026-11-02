package com.piga21.wallet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A deposit or withdrawal as the server last described it. The app never decides a status itself.
 */
public class WalletTransaction {
    public static final String PROCESSING = "processing";
    public static final String SUCCESSFUL = "successful";
    public static final String FAILED = "failed";
    public static final String REVERSED = "reversed";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH);

    public final String reference, type, status, phone, failureMessage, createdAt, completedAt;
    public final long amount, fee, total;

    WalletTransaction(JsonObject json) {
        reference = text(json, "reference");
        type = text(json, "type");
        status = text(json, "status");
        phone = text(json, "phone");
        failureMessage = text(json, "failure_message");
        createdAt = text(json, "created_at");
        completedAt = text(json, "completed_at");
        amount = number(json, "amount");
        fee = number(json, "fee");
        total = number(json, "total");
    }

    public boolean isDeposit() {
        return "deposit".equals(type);
    }

    public boolean isFinished() {
        return SUCCESSFUL.equals(status) || FAILED.equals(status) || REVERSED.equals(status);
    }

    /**
     * Short form of the reference for lists; the full one is shown on the details screen.
     */
    public String shortReference() {
        return reference == null ? "" : reference.substring(0, Math.min(8, reference.length())).toUpperCase(Locale.ENGLISH);
    }

    public String statusText() {
        if (SUCCESSFUL.equals(status)) return "Successful";
        if (FAILED.equals(status)) return "Failed";
        if (REVERSED.equals(status)) return "Reversed";
        return "Processing";
    }

    public String createdText() {
        return formatDate(createdAt);
    }

    static String formatDate(String iso) {
        if (iso == null) return "";
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DATE_FORMAT);
        } catch (Exception e) {
            return "";
        }
    }

    private static String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static long number(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? 0 : value.getAsLong();
    }
}
