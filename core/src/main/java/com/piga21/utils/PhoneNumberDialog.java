package com.piga21.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.google.gson.JsonObject;

import java.util.function.Consumer;

import com.piga21.AssetStore;

/**
 * The player's mobile money number, used for top ups and redemptions so they don't type it each
 * time. Laid out like the nickname editor: one row at the top, clear of the on-screen keyboard.
 * Saving it empty removes it, and the wallet asks for a number again.
 */
public class PhoneNumberDialog {
    private static final int DIGITS = 10;

    /**
     * @param onSaved  gets the updated profile from the server
     * @param onCancel called when the player backs out
     */
    public static void show(Stage stage, AssetStore assetStore, String current,
                            Consumer<JsonObject> onSaved, Runnable onCancel) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.85f);

        Table panel = new Table();
        panel.setBackground(UiKit.panel());

        Label title = GameText.title(assetStore, "MOBILE MONEY NUMBER");
        Label subtitle = GameText.label(assetStore.listFont, "Top ups come from this number and redemptions are paid to it.");
        subtitle.setColor(UiKit.MUTED);

        TextField field = UiKit.textField(assetStore, current == null ? "" : current, "07xxxxxxxx");
        field.setMaxLength(DIGITS);
        field.setTextFieldFilter((textField, c) -> Character.isDigit(c));
        field.setOnscreenKeyboard(visible -> Gdx.input.setOnscreenKeyboardVisible(visible, Input.OnscreenKeyboardType.PhonePad));

        Label hint = GameText.label(assetStore.listFont, "");

        TextButton[] save = new TextButton[1];
        Runnable close = () -> {
            Gdx.input.setOnscreenKeyboardVisible(false);
            stage.setKeyboardFocus(null);
            CommonGameFunctions.hideOverlayScreen(true);
        };
        Runnable submit = () -> {
            String number = field.getText().trim();
            if (!isValid(number) || save[0].isDisabled()) return;

            save[0].setDisabled(true);
            save[0].setText("SAVING...");
            PlayerApi.updatePhoneNumber(number, new PlayerApi.Callback() {
                @Override
                public void onSuccess(JsonObject profile) {
                    close.run();
                    onSaved.accept(profile);
                }

                @Override
                public void onFailure(String message) {
                    // Keep it open so they can fix it (e.g. not an MTN or Airtel number)
                    save[0].setText("SAVE");
                    save[0].setDisabled(false);
                    hint.setText(PlayerApi.readableMessage(message));
                    hint.setColor(UiKit.ERROR);
                }
            });
        };

        TextButton cancel = UiKit.button(assetStore, "CANCEL", UiKit.Style.SECONDARY, () -> {
            close.run();
            onCancel.run();
        });
        save[0] = UiKit.button(assetStore, "SAVE", UiKit.Style.PRIMARY, submit);

        Runnable refreshHint = () -> {
            String number = field.getText().trim();
            save[0].setDisabled(!isValid(number));
            hint.setColor(UiKit.MUTED);
            if (number.isEmpty()) {
                hint.setText("Leave it empty to remove your number");
            } else if (!number.startsWith("07")) {
                hint.setText("Start with 07, e.g. 0772123456");
            } else {
                hint.setText(number.length() + " / " + DIGITS + " digits");
            }
        };
        field.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                submit.run();
            } else {
                refreshHint.run();
            }
        });
        refreshHint.run();

        panel.add(title).colspan(3).padBottom(4).row();
        panel.add(subtitle).colspan(3).padBottom(22).row();
        panel.add(field).width(720).height(100).padRight(24);
        panel.add(cancel).width(250).height(100).padRight(20);
        panel.add(save[0]).width(250).height(100).row();
        panel.add(hint).colspan(3).left().padTop(10).row();
        panel.pack();

        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() - panel.getHeight() - 20);
        overlay.addActor(panel);
        CommonGameFunctions.showOverlay();

        stage.setKeyboardFocus(field);
        field.setCursorPosition(field.getText().length());
        Gdx.input.setOnscreenKeyboardVisible(true);
    }

    /**
     * Empty is allowed: it removes the number. The server checks the network.
     */
    private static boolean isValid(String number) {
        return number.isEmpty() || (number.length() == DIGITS && number.startsWith("07"));
    }
}
