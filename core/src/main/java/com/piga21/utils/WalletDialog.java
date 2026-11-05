package com.piga21.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

import com.piga21.AssetStore;
import com.piga21.wallet.WalletClient;
import com.piga21.wallet.WalletTransaction;

/**
 * Deposit / withdraw, in three steps: the form (phone number and amount), a review screen,
 * then the live status until the server reports the result. Everything shown about money comes
 * from the server through WalletClient; the phone never marks anything as paid.
 */
public class WalletDialog implements WalletClient.Listener {
    private static final int PHONE_DIGITS = 10;
    private static final int STATUS_CHECK_SECONDS = 3;
    private static final int MAX_AMOUNT_DIGITS = 9;
    private static final Color SUCCESS = Color.valueOf("#5BD47A");

    // The dialog on screen, if any, so a second tap doesn't open another
    private static WalletDialog current;

    private final Stage stage;
    private final AssetStore assetStore;
    private final WalletClient wallet;
    private final boolean deposit;
    private OverlayContainer overlay;
    private Table panel;

    // Kept so Back and Try again come back to what the player typed
    private String phone = "", amountText = "";
    // The request we're waiting on, then the transaction it became
    private String requestId, reference;
    private boolean timedOut;
    private Runnable refreshForm;

    private WalletDialog(Stage stage, AssetStore assetStore, boolean deposit) {
        this.stage = stage;
        this.assetStore = assetStore;
        this.wallet = WalletClient.get();
        this.deposit = deposit;
    }

    public static void show(Stage stage, AssetStore assetStore, boolean deposit) {
        WalletDialog dialog = open(stage, assetStore, deposit);
        dialog.showForm(null);
    }

    /**
     * Details and live status of an existing transaction, e.g. tapped in the history.
     */
    public static void showTransaction(Stage stage, AssetStore assetStore, WalletTransaction transaction) {
        WalletDialog dialog = open(stage, assetStore, transaction.isDeposit());
        dialog.reference = transaction.reference;
        dialog.showStatus(transaction);
    }

    /**
     * Whether the player is already watching this transaction (so no extra toast is needed).
     */
    public static boolean isShowing(String reference) {
        return current != null && current.isAlive() && reference != null && reference.equals(current.reference);
    }

    private static WalletDialog open(Stage stage, AssetStore assetStore, boolean deposit) {
        if (current != null) WalletClient.get().removeListener(current);
        WalletDialog dialog = new WalletDialog(stage, assetStore, deposit);
        dialog.overlay = CommonGameFunctions.initializeOverlay(stage, 0.85f);
        CommonGameFunctions.showOverlay();
        WalletClient.get().addListener(dialog);
        if (!WalletClient.get().isLoaded()) WalletClient.get().refresh();
        current = dialog;
        return dialog;
    }

    /* Steps */

    private void showForm(String error) {
        Table panel = newPanel();
        String action = deposit ? "TOP UP" : "REDEEM";

        Label subtitle = muted("");
        TextField phoneField = UiKit.textField(assetStore, phone, "07xxxxxxxx");
        phoneField.setMaxLength(PHONE_DIGITS);
        phoneField.setTextFieldFilter((textField, c) -> Character.isDigit(c));
        phoneField.setOnscreenKeyboard(visible -> Gdx.input.setOnscreenKeyboardVisible(visible, Input.OnscreenKeyboardType.PhonePad));

        TextField amountField = UiKit.textField(assetStore, amountText, "Points");
        amountField.setMaxLength(MAX_AMOUNT_DIGITS);
        amountField.setTextFieldFilter((textField, c) -> Character.isDigit(c));
        amountField.setOnscreenKeyboard(visible -> Gdx.input.setOnscreenKeyboardVisible(visible, Input.OnscreenKeyboardType.NumberPad));

        Label hint = GameText.label(assetStore.listFont, "");
        hint.setWrap(true);

        TextButton cancel = UiKit.button(assetStore, "CANCEL", UiKit.Style.SECONDARY, this::close);
        TextButton next = UiKit.button(assetStore, "CONTINUE", UiKit.Style.PRIMARY, () -> {
            phone = phoneField.getText();
            amountText = amountField.getText();
            if (validate(phone, amount()) == null) {
                hideKeyboard();
                showReview(null);
            }
        });

        // A refused request's reason stays up until the player changes something
        String[] serverError = {error};
        refreshForm = () -> {
            phone = phoneField.getText();
            amountText = amountField.getText();
            if (!wallet.isConnected()) {
                subtitle.setText("You're offline. Reconnecting...");
            } else if (!wallet.isLoaded()) {
                subtitle.setText("Loading your points...");
            } else if (deposit) {
                subtitle.setText("Add money from your mobile money account.");
            } else {
                subtitle.setText("You can redeem " + wallet.money(wallet.getRedeemable())
                    + (wallet.getRedeemable() < wallet.getBalance() ? "  ·  welcome points are for play only" : ""));
            }

            String problem = validate(phone, amount());
            next.setDisabled(problem != null);
            if (serverError[0] != null) {
                hint.setText(serverError[0]);
                hint.setColor(UiKit.ERROR);
            } else if (problem != null) {
                hint.setText(problem);
                hint.setColor(UiKit.MUTED);
            } else {
                hint.setText(feeSummary(amount()));
                hint.setColor(UiKit.MUTED);
            }
        };

        phoneField.setTextFieldListener((textField, c) -> {
            serverError[0] = null;
            if (c == '\r' || c == '\n') {
                stage.setKeyboardFocus(amountField);
                amountField.getOnscreenKeyboard().show(true);
            }
            refreshForm.run();
        });
        amountField.setTextFieldListener((textField, c) -> {
            serverError[0] = null;
            refreshForm.run();
            if ((c == '\r' || c == '\n') && !next.isDisabled()) {
                hideKeyboard();
                showReview(null);
            }
        });
        refreshForm.run();

        Table buttons = new Table();
        buttons.add(cancel).width(300).height(100).padRight(40);
        buttons.add(next).width(300).height(100);

        panel.add(GameText.title(assetStore, action)).padBottom(8).row();
        panel.add(subtitle).padBottom(26).row();
        // A number saved on their profile is used as it is: only the points are asked for
        String savedPhone = wallet.getSavedPhone();
        if (!deposit) {
            // Redemptions only go to the number last topped up from: no number to type, and a
            // note saying why, so nobody can be talked into redeeming to someone else's phone
            String redeemPhone = wallet.getRedeemPhone();
            phone = redeemPhone == null ? "" : redeemPhone;
            phoneField.setText(phone);
            Label note = GameText.label(assetStore.listFont, redeemPhone == null
                ? "Points can only be redeemed to the number you top up from. Top up first to redeem."
                : "Paid only to " + WalletClient.formatPhone(redeemPhone) + ", the number you last topped up from. "
                    + "This keeps your points safe: no one can redeem them to another phone.");
            note.setColor(UiKit.GOLD);
            note.setWrap(true);
            panel.add(note).width(780).left().padBottom(16).row();
        } else if (savedPhone != null) {
            phone = savedPhone;
            phoneField.setText(savedPhone);
            Label number = GameText.label(assetStore.listFont, (deposit ? "From " : "To ") + WalletClient.formatPhone(savedPhone)
                + "   ·   change it in your profile");
            number.setColor(UiKit.MUTED);
            panel.add(number).width(780).left().padBottom(14).row();
        } else {
            panel.add(phoneField).width(780).height(104).padBottom(20).row();
        }
        panel.add(amountField).width(780).height(104).row();
        panel.add(hint).width(780).left().padTop(12).padBottom(24).row();
        panel.add(buttons).row();

        // Top half: the keyboard takes the bottom half in landscape
        place(panel, true);

        // The keyboard opens only when a field is tapped; tapping anywhere else on the panel puts it away
        panel.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (!(event.getTarget() instanceof TextField)) hideKeyboard();
                return false;
            }
        });
        // Keep the offline / loading line current
        panel.addAction(Actions.forever(Actions.sequence(Actions.delay(1f), Actions.run(refreshForm))));
    }

    private void showReview(String error) {
        refreshForm = null;
        Table panel = newPanel();
        long amount = amount();
        long fee = deposit ? wallet.getDepositFee() : wallet.getWithdrawalFee();

        panel.add(GameText.title(assetStore, deposit ? "CONFIRM TOP UP" : "CONFIRM REDEEM")).colspan(2).padBottom(26).row();
        addRow(panel, deposit ? "From" : "To", WalletClient.formatPhone(phone));
        addRow(panel, "Points", wallet.money(amount));
        addRow(panel, "Service points", fee == 0 ? "None" : wallet.money(fee));
        addRow(panel, deposit ? "You pay" : "Taken from your points", wallet.money(amount + fee));

        Label note = muted(deposit
            ? "You'll get a mobile money prompt. Enter your PIN to approve."
            : "Paid only to the number you last topped up from. Piga21 will never ask you to redeem to another number.");
        note.setWrap(true);
        note.setAlignment(Align.center);
        panel.add(note).colspan(2).width(760).padTop(20).row();

        if (error != null) {
            Label errorLabel = GameText.label(assetStore.listFont, error);
            errorLabel.setColor(UiKit.ERROR);
            errorLabel.setWrap(true);
            errorLabel.setAlignment(Align.center);
            panel.add(errorLabel).colspan(2).width(760).padTop(14).row();
        }

        TextButton back = UiKit.button(assetStore, "BACK", UiKit.Style.SECONDARY, () -> showForm(null));
        TextButton[] confirm = new TextButton[1];
        confirm[0] = UiKit.button(assetStore, "CONFIRM", UiKit.Style.PRIMARY, () -> {
            // Sent over HTTP; the socket only brings the status updates afterwards
            if (!wallet.isConnected()) wallet.reconnect();
            // One tap, one request: the buttons go away before anything is sent
            confirm[0].setDisabled(true);
            back.setDisabled(true);
            timedOut = false;
            requestId = deposit ? wallet.deposit(amount, phone) : wallet.withdraw(amount, phone);
            showSending();
        });

        Table buttons = new Table();
        buttons.add(back).width(300).height(100).padRight(40);
        buttons.add(confirm[0]).width(300).height(100);
        panel.add(buttons).colspan(2).padTop(30).row();

        place(panel, false);
    }

    /**
     * Sent, but the server hasn't acknowledged it yet.
     */
    private void showSending() {
        Table panel = newPanel();
        panel.add(GameText.title(assetStore, deposit ? "TOP UP" : "REDEEM")).padBottom(20).row();
        panel.add(spinner()).size(120, 120).padBottom(20).row();

        if (!timedOut) {
            panel.add(message("Sending your request...")).width(760).row();
        } else {
            panel.add(message("The server hasn't answered yet.")).width(760).padBottom(8).row();
            Label detail = muted("Check your connection. You can try again safely. It won't be charged twice.");
            detail.setWrap(true);
            detail.setAlignment(Align.center);
            panel.add(detail).width(760).row();

            Table buttons = new Table();
            buttons.add(UiKit.button(assetStore, "CLOSE", UiKit.Style.SECONDARY, this::close)).width(300).height(100).padRight(40);
            buttons.add(UiKit.button(assetStore, "TRY AGAIN", UiKit.Style.PRIMARY, () -> {
                if (!wallet.isConnected()) wallet.reconnect();
                timedOut = false;
                wallet.retryPending();
                showSending();
            })).width(300).height(100);
            panel.add(buttons).padTop(30).row();
        }
        place(panel, false);
    }

    private void showStatus(WalletTransaction transaction) {
        refreshForm = null;
        Table panel = newPanel();
        boolean success = WalletTransaction.SUCCESSFUL.equals(transaction.status);
        boolean failed = WalletTransaction.FAILED.equals(transaction.status);
        String kind = transaction.isDeposit() ? "TOP UP" : "REDEEM";

        Label title = GameText.title(assetStore, success ? kind + " SUCCESSFUL" : failed ? kind + " FAILED"
            : WalletTransaction.REVERSED.equals(transaction.status) ? kind + " REVERSED" : "PROCESSING");
        panel.add(title).colspan(2).padBottom(16).row();
        if (!transaction.isFinished()) {
            panel.add(spinner()).colspan(2).size(110, 110).padBottom(16).row();
        }

        Label headline = message(headline(transaction));
        headline.setColor(success ? SUCCESS : failed ? UiKit.ERROR : Color.WHITE);
        panel.add(headline).colspan(2).width(820).padBottom(22).row();

        addRow(panel, transaction.isDeposit() ? "From" : "To", transaction.phone);
        addRow(panel, "Points", wallet.money(transaction.amount));
        if (transaction.fee > 0) addRow(panel, "Service points", wallet.money(transaction.fee));
        addRow(panel, "Date", transaction.createdText());
        addRow(panel, "Reference", transaction.reference);

        Label balance = muted("Points balance: " + wallet.money(wallet.getBalance())
            + (wallet.getReserved() > 0 ? "  ·  " + wallet.money(wallet.getReserved()) + " on hold" : ""));
        panel.add(balance).colspan(2).padTop(16).row();

        Label connection = GameText.label(assetStore.listFont, "");
        connection.setColor(UiKit.ERROR);
        panel.add(connection).colspan(2).padTop(8).row();
        Runnable updateConnection = () -> connection.setText(!transaction.isFinished() && !wallet.isConnected()
            ? "Connection lost. Reconnecting to get the latest status..." : "");
        updateConnection.run();
        // The server's push normally brings the result; while it's still processing, also ask every
        // few seconds, so a missed push never leaves the player waiting
        int[] seconds = {0};
        panel.addAction(Actions.forever(Actions.sequence(Actions.delay(1f), Actions.run(() -> {
            if (!wallet.isConnected()) wallet.reconnect();
            updateConnection.run();
            if (!transaction.isFinished() && ++seconds[0] % STATUS_CHECK_SECONDS == 0) {
                wallet.refreshTransaction(transaction.reference);
            }
        }))));

        Table buttons = new Table();
        if (failed) {
            buttons.add(UiKit.button(assetStore, "DONE", UiKit.Style.SECONDARY, this::close)).width(300).height(100).padRight(40);
            // A new transaction (new request ID); the failed one is finished and can't be charged
            buttons.add(UiKit.button(assetStore, "TRY AGAIN", UiKit.Style.PRIMARY, () -> {
                reference = null;
                requestId = null;
                if (phone.isEmpty()) amountText = String.valueOf(transaction.amount);
                showForm(null);
            })).width(300).height(100);
        } else if (transaction.isFinished()) {
            buttons.add(UiKit.button(assetStore, "DONE", UiKit.Style.PRIMARY, this::close)).width(300).height(100);
        } else {
            Label later = muted("You can close this. Your points update when it completes.");
            panel.add(later).colspan(2).padTop(6).row();
            buttons.add(UiKit.button(assetStore, "CLOSE", UiKit.Style.SECONDARY, this::close)).width(300).height(100);
        }
        panel.add(buttons).colspan(2).padTop(24).row();

        place(panel, false);
    }

    private String headline(WalletTransaction transaction) {
        String money = wallet.money(transaction.amount);
        switch (transaction.status) {
            case WalletTransaction.SUCCESSFUL:
                return transaction.isDeposit() ? money + " has been added to your points." : money + " has been sent to " + transaction.phone + ".";
            case WalletTransaction.FAILED:
                return transaction.failureMessage != null ? transaction.failureMessage : "The transaction did not go through.";
            case WalletTransaction.REVERSED:
                return "The network reversed this transaction. Our team will review it.";
            default:
                if (!transaction.isDeposit()) return "Sending " + money + " to " + transaction.phone + "...";
                return "Check your phone for the mobile money prompt and enter your PIN.";
        }
    }

    /* Server events */

    @Override
    public void onRequestAccepted(String requestId, WalletTransaction transaction) {
        if (!isAlive() || requestId == null || !requestId.equals(this.requestId)) return;
        reference = transaction.reference;
        showStatus(transaction);
    }

    @Override
    public void onRequestFailed(String requestId, String code, String message) {
        if (!isAlive() || requestId == null || !requestId.equals(this.requestId)) return;
        if ("server_error".equals(code)) {
            // Nothing was created; the same request can be sent again safely
            timedOut = true;
            showSending();
            return;
        }
        this.requestId = null;
        showForm(message);
    }

    @Override
    public void onRequestTimedOut(String requestId) {
        if (!isAlive() || !requestId.equals(this.requestId) || reference != null) return;
        timedOut = true;
        showSending();
    }

    @Override
    public void onTransactionUpdated(WalletTransaction transaction) {
        if (!isAlive() || !transaction.reference.equals(reference)) return;
        showStatus(transaction);
    }

    @Override
    public void onWalletChanged() {
        if (!isAlive()) return;
        if (refreshForm != null) refreshForm.run();
    }

    /* Helpers */

    /**
     * False once the overlay was closed (by a button, or the back key); we then stop listening.
     */
    private boolean isAlive() {
        boolean alive = overlay != null && overlay.hasParent() && CommonGameFunctions.overlayContainer == overlay;
        if (!alive) {
            wallet.removeListener(this);
            if (current == this) current = null;
        }
        return alive;
    }

    private void close() {
        hideKeyboard();
        wallet.removeListener(this);
        if (current == this) current = null;
        CommonGameFunctions.hideOverlayScreen(true);
    }

    private Table newPanel() {
        if (panel != null) panel.remove();
        refreshForm = null;
        panel = new Table();
        panel.setBackground(UiKit.panel());
        return panel;
    }

    private void place(Table panel, boolean top) {
        panel.pack();
        float y = top ? stage.getHeight() - panel.getHeight() - 30 : stage.getHeight() / 2 - panel.getHeight() / 2;
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, Math.max(10, y));
        overlay.addActor(panel);
    }

    private void hideKeyboard() {
        stage.setKeyboardFocus(null);
        Gdx.input.setOnscreenKeyboardVisible(false);
    }

    private long amount() {
        try {
            return amountText.isEmpty() ? 0 : Long.parseLong(amountText);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * What's wrong with the form, or null if it can go to review. Display only: the server checks again.
     */
    private String validate(String phone, long amount) {
        if (!deposit && wallet.isLoaded() && wallet.getRedeemPhone() == null) return "Top up first to redeem";
        if (phone.length() < PHONE_DIGITS || phone.charAt(0) != '0') return "Enter your 10-digit number, e.g. 0772123456";
        if (amount <= 0) return "Enter an amount";
        long minimum = deposit ? wallet.getMinAmount() : wallet.getMinWithdrawalAmount();
        if (amount < minimum) return (deposit ? "The minimum is " : "The minimum you can redeem is ") + wallet.money(minimum);
        if (amount > wallet.getMaxAmount()) return "The maximum is " + wallet.money(wallet.getMaxAmount());
        if (!deposit) {
            if (!wallet.areWithdrawalsEnabled()) return "Redeeming is disabled for your account";
            // Until the wallet has loaded the balance is unknown; the server checks it
            if (!wallet.isLoaded()) return null;
            long total = amount + wallet.getWithdrawalFee();
            if (total > wallet.getBalance()) return "Not enough points (" + wallet.money(wallet.getBalance()) + " available)";
            if (total > wallet.getRedeemable()) return "Welcome points are for play only. You can redeem " + wallet.money(wallet.getRedeemable());
        }
        return null;
    }

    private String feeSummary(long amount) {
        long fee = deposit ? wallet.getDepositFee() : wallet.getWithdrawalFee();
        if (fee == 0) return "No service points";
        return "Service points " + wallet.money(fee) + "  ·  " + (deposit ? "You pay " : "Taken from your points ") + wallet.money(amount + fee);
    }

    private void addRow(Table table, String name, String value) {
        Label nameLabel = GameText.label(assetStore.listFont, name);
        nameLabel.setColor(UiKit.MUTED);
        table.add(nameLabel).right().padRight(30).padBottom(10);
        table.add(GameText.label(assetStore.listFont, value)).left().padBottom(10).row();
    }

    private Label muted(String text) {
        Label label = GameText.label(assetStore.listFont, text);
        label.setColor(UiKit.MUTED);
        return label;
    }

    private Label message(String text) {
        Label label = GameText.label(assetStore.messageFont, text);
        label.setWrap(true);
        label.setAlignment(Align.center);
        return label;
    }

    private Image spinner() {
        Image chip = new Image(assetStore.otherComponents.findRegion("royal_chip"));
        chip.setSize(110, 110);
        chip.setOrigin(Align.center);
        chip.addAction(Actions.forever(Actions.rotateBy(-360f, 1.4f)));
        return chip;
    }
}
