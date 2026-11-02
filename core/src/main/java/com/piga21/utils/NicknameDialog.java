package com.piga21.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.google.gson.JsonObject;

import java.util.function.Consumer;

import com.piga21.AssetStore;

/**
 * In-game nickname editor: a text field with live checks and Save / Cancel beside it. It sits
 * at the top of the screen, in one row, because the on-screen keyboard covers most of the
 * screen in landscape. Saving it empty clears the nickname so the player's name shows again.
 */
public class NicknameDialog {
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 20;
    private static final String ALLOWED_SYMBOLS = " ._-";

    /**
     * @param name     the player's own name, shown when they have no nickname
     * @param onSaved  gets the updated profile from the server
     * @param onCancel called when the player backs out
     */
    public static void show(Stage stage, AssetStore assetStore, String current, String name,
                            Consumer<JsonObject> onSaved, Runnable onCancel) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.85f);

        Table panel = new Table();
        panel.setBackground(UiKit.panel());

        Label title = GameText.title(assetStore, "CHOOSE A NICKNAME");
        Label subtitle = GameText.label(assetStore.listFont, "This is the name other players see in games and on the leaderboard.");
        subtitle.setColor(UiKit.MUTED);

        TextField field = UiKit.textField(assetStore, current == null ? "" : current, "e.g. Lucky Ace");
        field.setMaxLength(MAX_LENGTH);
        field.setTextFieldFilter((textField, c) -> Character.isLetterOrDigit(c) || ALLOWED_SYMBOLS.indexOf(c) >= 0);

        Label hint = GameText.label(assetStore.listFont, "");

        TextButton[] save = new TextButton[1];
        Runnable close = () -> {
            Gdx.input.setOnscreenKeyboardVisible(false);
            stage.setKeyboardFocus(null);
            CommonGameFunctions.hideOverlayScreen(true);
        };
        Runnable submit = () -> {
            String nickName = field.getText().trim();
            if (!isValid(nickName) || save[0].isDisabled()) return;

            save[0].setDisabled(true);
            save[0].setText("SAVING...");
            PlayerApi.updateNickName(nickName, new PlayerApi.Callback() {
                @Override
                public void onSuccess(JsonObject profile) {
                    close.run();
                    onSaved.accept(profile);
                }

                @Override
                public void onFailure(String message) {
                    // Keep the dialog open so they can fix it (e.g. "That nickname is taken")
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
            String nickName = field.getText().trim();
            save[0].setDisabled(!isValid(nickName));
            hint.setColor(UiKit.MUTED);
            if (nickName.isEmpty()) {
                hint.setText("Leave it empty to use your name" + (name == null || name.isEmpty() ? "" : ", " + name));
            } else if (nickName.length() < MIN_LENGTH) {
                hint.setText("At least " + MIN_LENGTH + " characters (letters, numbers, spaces, . _ -)");
            } else {
                hint.setText(nickName.length() + " / " + MAX_LENGTH);
            }
        };
        field.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                submit.run(); // keyboard's Done/Enter saves
            } else {
                refreshHint.run();
            }
        });
        refreshHint.run();

        // Buttons sit beside the field so the keyboard never covers them
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
     * Empty is allowed: it clears the nickname.
     */
    private static boolean isValid(String nickName) {
        return nickName.isEmpty() || (nickName.length() >= MIN_LENGTH && nickName.length() <= MAX_LENGTH);
    }
}
