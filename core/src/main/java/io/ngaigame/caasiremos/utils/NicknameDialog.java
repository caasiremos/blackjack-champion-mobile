package io.ngaigame.caasiremos.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.google.gson.JsonObject;

import java.util.function.Consumer;

import io.ngaigame.caasiremos.AssetStore;

/**
 * In-game nickname editor: a text field with live checks and Save / Cancel. It sits in the
 * top half of the screen because the on-screen keyboard covers the bottom half in landscape.
 */
public class NicknameDialog {
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 20;
    private static final String ALLOWED_SYMBOLS = " ._-";

    /**
     * @param onSaved  gets the updated profile from the server
     * @param onCancel called when the player backs out
     */
    public static void show(Stage stage, AssetStore assetStore, String current,
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
            if (nickName.length() < MIN_LENGTH) {
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

        Table buttons = new Table();
        buttons.add(cancel).width(300).height(100).padRight(40);
        buttons.add(save[0]).width(300).height(100);

        panel.add(title).padBottom(8).row();
        panel.add(subtitle).padBottom(34).row();
        panel.add(field).width(760).height(104).row();
        panel.add(hint).left().padTop(12).padBottom(30).row();
        panel.add(buttons).row();
        panel.pack();

        // Top half: the keyboard takes the bottom half in landscape
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() - panel.getHeight() - 50);
        overlay.addActor(panel);
        CommonGameFunctions.showOverlay();

        stage.setKeyboardFocus(field);
        field.setCursorPosition(field.getText().length());
        Gdx.input.setOnscreenKeyboardVisible(true);
    }

    private static boolean isValid(String nickName) {
        return nickName.length() >= MIN_LENGTH && nickName.length() <= MAX_LENGTH;
    }
}
