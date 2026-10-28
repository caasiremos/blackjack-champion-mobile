package io.ngaigame.caasiremos.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;

import io.ngaigame.caasiremos.AssetStore;

/**
 * Deposit / withdraw form: a phone number and an amount with Cancel / Confirm. Like the
 * nickname editor it sits in the top half, clear of the on-screen keyboard.
 */
public class WalletDialog {
    private static final int MIN_PHONE_DIGITS = 9;
    private static final int MAX_PHONE_DIGITS = 15;
    private static final int MAX_AMOUNT_DIGITS = 9;

    public interface Listener {
        void onConfirm(String phoneNumber, long amount);
    }

    /**
     * @param title     e.g. "DEPOSIT" or "WITHDRAW"; also used as the confirm button's label
     * @param onConfirm gets the checked phone number and amount
     */
    public static void show(Stage stage, AssetStore assetStore, String title, String subtitleText, Listener onConfirm) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.85f);

        Table panel = new Table();
        panel.setBackground(UiKit.panel());

        Label titleLabel = GameText.title(assetStore, title);
        Label subtitle = GameText.label(assetStore.listFont, subtitleText);
        subtitle.setColor(UiKit.MUTED);

        TextField phoneField = UiKit.textField(assetStore, "", "Phone number");
        phoneField.setMaxLength(MAX_PHONE_DIGITS + 1);
        // A leading + for the country code, digits otherwise
        phoneField.setTextFieldFilter((textField, c) -> Character.isDigit(c) || (c == '+' && textField.getCursorPosition() == 0));
        phoneField.setOnscreenKeyboard(visible -> Gdx.input.setOnscreenKeyboardVisible(visible, Input.OnscreenKeyboardType.PhonePad));

        TextField amountField = UiKit.textField(assetStore, "", "Amount");
        amountField.setMaxLength(MAX_AMOUNT_DIGITS);
        amountField.setTextFieldFilter((textField, c) -> Character.isDigit(c));
        amountField.setOnscreenKeyboard(visible -> Gdx.input.setOnscreenKeyboardVisible(visible, Input.OnscreenKeyboardType.NumberPad));

        Label hint = GameText.label(assetStore.listFont, "");

        TextButton[] confirm = new TextButton[1];
        Runnable close = () -> {
            Gdx.input.setOnscreenKeyboardVisible(false);
            stage.setKeyboardFocus(null);
            CommonGameFunctions.hideOverlayScreen(true);
        };
        Runnable refreshHint = () -> {
            String phone = phoneField.getText();
            long amount = parseAmount(amountField.getText());
            confirm[0].setDisabled(!isValidPhone(phone) || amount <= 0);
            hint.setColor(UiKit.MUTED);
            if (!isValidPhone(phone)) {
                hint.setText("Enter a phone number (" + MIN_PHONE_DIGITS + " to " + MAX_PHONE_DIGITS + " digits)");
            } else if (amount <= 0) {
                hint.setText("Enter an amount greater than 0");
            } else {
                hint.setText("");
            }
        };
        Runnable submit = () -> {
            String phone = phoneField.getText();
            long amount = parseAmount(amountField.getText());
            if (!isValidPhone(phone) || amount <= 0 || confirm[0].isDisabled()) return;

            close.run();
            onConfirm.onConfirm(phone, amount);
        };

        TextButton cancel = UiKit.button(assetStore, "CANCEL", UiKit.Style.SECONDARY, close);
        confirm[0] = UiKit.button(assetStore, title, UiKit.Style.PRIMARY, submit);

        // Enter on the phone number moves on to the amount; Enter on the amount confirms
        phoneField.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                stage.setKeyboardFocus(amountField);
                amountField.getOnscreenKeyboard().show(true);
            } else {
                refreshHint.run();
            }
        });
        amountField.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                submit.run();
            } else {
                refreshHint.run();
            }
        });
        refreshHint.run();

        Table buttons = new Table();
        buttons.add(cancel).width(300).height(100).padRight(40);
        buttons.add(confirm[0]).width(300).height(100);

        panel.add(titleLabel).padBottom(8).row();
        panel.add(subtitle).padBottom(30).row();
        panel.add(phoneField).width(760).height(104).padBottom(20).row();
        panel.add(amountField).width(760).height(104).row();
        panel.add(hint).left().padTop(12).padBottom(26).row();
        panel.add(buttons).row();
        panel.pack();

        // Top half: the keyboard takes the bottom half in landscape
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() - panel.getHeight() - 30);
        overlay.addActor(panel);
        CommonGameFunctions.showOverlay();

        stage.setKeyboardFocus(phoneField);
        phoneField.getOnscreenKeyboard().show(true);
    }

    private static boolean isValidPhone(String phone) {
        String digits = phone.startsWith("+") ? phone.substring(1) : phone;
        return digits.length() >= MIN_PHONE_DIGITS && digits.length() <= MAX_PHONE_DIGITS;
    }

    private static long parseAmount(String text) {
        try {
            return text.isEmpty() ? 0 : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
