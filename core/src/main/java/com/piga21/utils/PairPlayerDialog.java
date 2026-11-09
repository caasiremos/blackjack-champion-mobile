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
 * Pair with a player: type their 6-character player ID and send them a game request. Your own
 * ID is shown underneath so you can give it to a friend. Like the nickname editor, it sits at
 * the top of the screen in one row, because the on-screen keyboard covers most of the screen.
 */
public class PairPlayerDialog {
    public static final int CODE_LENGTH = 6;

    private final Stage stage;
    private final Label hint;
    private TextButton send;
    private boolean open = true;

    /**
     * @param onSend gets the typed ID (upper case); the server answers with request_sent or an error
     */
    public static PairPlayerDialog show(Stage stage, AssetStore assetStore, Consumer<String> onSend) {
        return new PairPlayerDialog(stage, assetStore, onSend);
    }

    private PairPlayerDialog(Stage stage, AssetStore assetStore, Consumer<String> onSend) {
        this.stage = stage;
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.85f);

        Label title = GameText.title(assetStore, "PAIR WITH A PLAYER");
        Label subtitle = GameText.label(assetStore.listFont, "Enter their player ID to send them a game request for their stake.");
        subtitle.setColor(UiKit.MUTED);

        TextField field = UiKit.textField(assetStore, "", "e.g. K7Q2XM");
        field.setMaxLength(CODE_LENGTH);
        field.setTextFieldFilter((textField, c) -> Character.isLetterOrDigit(c) && c < 128);

        hint = GameText.label(assetStore.listFont, "");
        Label mine = GameText.label(assetStore.listFont, "Your player ID: ...");
        mine.setColor(UiKit.GOLD);

        Runnable submit = () -> {
            String code = field.getText().trim().toUpperCase(java.util.Locale.US);
            if (code.length() != CODE_LENGTH || send.isDisabled()) return;
            send.setDisabled(true);
            send.setText("SENDING...");
            hint.setColor(UiKit.MUTED);
            hint.setText("Looking for " + code + "...");
            onSend.accept(code);
        };
        send = UiKit.button(assetStore, "SEND REQUEST", UiKit.Style.PRIMARY, submit);
        TextButton cancel = UiKit.button(assetStore, "CANCEL", UiKit.Style.SECONDARY, this::close);

        Runnable refresh = () -> {
            // Typed letters show in capitals, like the IDs themselves
            String upper = field.getText().toUpperCase(java.util.Locale.US);
            if (!upper.equals(field.getText())) {
                int cursor = field.getCursorPosition();
                field.setText(upper);
                field.setCursorPosition(cursor);
            }
            int length = upper.length();
            send.setDisabled(length != CODE_LENGTH);
            hint.setColor(UiKit.MUTED);
            hint.setText(length == CODE_LENGTH ? "Ready to send" : "Player IDs have " + CODE_LENGTH + " letters and numbers (" + length + " / " + CODE_LENGTH + ")");
        };
        field.setTextFieldListener((textField, c) -> {
            if (c == '\r' || c == '\n') {
                submit.run(); // keyboard's Done sends
            } else {
                refresh.run();
            }
        });
        refresh.run();

        Table panel = new Table();
        panel.setBackground(UiKit.panel());
        panel.add(title).colspan(3).padBottom(4).row();
        panel.add(subtitle).colspan(3).padBottom(22).row();
        panel.add(field).width(560).height(100).padRight(24);
        panel.add(cancel).width(250).height(100).padRight(20);
        panel.add(send).width(330).height(100).row();
        panel.add(hint).colspan(3).left().padTop(10).row();
        panel.add(mine).colspan(3).left().padTop(4).row();
        panel.pack();
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() - panel.getHeight() - 20);
        overlay.addActor(panel);
        CommonGameFunctions.showOverlay();

        stage.setKeyboardFocus(field);
        Gdx.input.setOnscreenKeyboardVisible(true);

        PlayerApi.fetchProfile(new PlayerApi.Callback() {
            @Override
            public void onSuccess(JsonObject player) {
                boolean has = player.has("player_code") && !player.get("player_code").isJsonNull();
                mine.setText(has ? "Your player ID: " + player.get("player_code").getAsString() + "   ·   share it so friends can pair with you" : "");
            }

            @Override
            public void onFailure(String message) {
                mine.setText("");
            }
        });
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * The server turned the request down (no such ID, offline, not enough points): say why and
     * let them try another ID.
     */
    public void showError(String message) {
        if (!open) return;
        send.setText("SEND REQUEST");
        send.setDisabled(false);
        hint.setColor(UiKit.ERROR);
        hint.setText(message);
    }

    /** Hide the keyboard; the request dialog (or nothing) takes the overlay's place. */
    public void dismiss() {
        open = false;
        Gdx.input.setOnscreenKeyboardVisible(false);
        stage.setKeyboardFocus(null);
    }

    public void close() {
        if (!open) return;
        dismiss();
        CommonGameFunctions.hideOverlayScreen(true);
    }
}
