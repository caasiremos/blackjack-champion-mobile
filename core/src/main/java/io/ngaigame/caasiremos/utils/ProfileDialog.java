package io.ngaigame.caasiremos.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.ngaigame.caasiremos.AssetStore;

/**
 * A player's profile as an overlay: names, rank and record. The player's own profile also
 * shows their email and chips and lets them change their nickname; another player's never
 * includes an email (the server doesn't send it) and offers a challenge instead.
 */
public class ProfileDialog {
    private static final Color LABEL_COLOR = Color.valueOf("#B8C4FF");
    private static final Color LINK_COLOR = Color.valueOf("#F4B331");

    /**
     * @param own         true for the signed-in player's own profile
     * @param status      e.g. "Online", "In a game", or null
     * @param onChallenge shown as a Challenge button for other players; null hides it
     */
    public static void show(Stage stage, AssetStore assetStore, CommonUtilInterface ui, JsonObject player,
                            boolean own, String status, Runnable onChallenge) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.85f);

        Table table = new Table();
        table.setBackground(UiKit.panel());

        Image avatar = new Image(assetStore.otherComponents.findRegion("avatar"));
        table.add(avatar).size(130, 130).padBottom(10).colspan(2).row();
        table.add(GameText.title(assetStore, displayName(player))).padBottom(status != null ? 4 : 30).colspan(2).row();
        if (status != null) {
            Label statusLabel = GameText.label(assetStore.listFont, status);
            statusLabel.setColor(LINK_COLOR);
            table.add(statusLabel).padBottom(30).colspan(2).row();
        }

        addRow(table, assetStore, "Name", text(player, "full_name", "-"));
        addRow(table, assetStore, "Nickname", text(player, "nick_name", "Not set"));
        if (own) {
            addRow(table, assetStore, "Email", text(player, "email", "-"));
            addRow(table, assetStore, "Chips", text(player, "balance", "0"));
        }
        addRow(table, assetStore, "Rank", player.has("rank") && !player.get("rank").isJsonNull()
            ? "#" + player.get("rank").getAsInt() : "Unranked");
        addRow(table, assetStore, "Chips won", text(player, "total_points", "0"));
        addRow(table, assetStore, "Record", "Won " + text(player, "total_wins", "0")
            + "  ·  Lost " + text(player, "total_losses", "0")
            + "  ·  Tied " + text(player, "total_draws", "0"));

        if (own) {
            TextButton change = UiKit.button(assetStore, "CHANGE NICKNAME", UiKit.Style.SECONDARY, () ->
                NicknameDialog.show(stage, assetStore, text(player, "nick_name", ""),
                    updated -> {
                        ui.showToast("Nickname saved");
                        show(stage, assetStore, ui, updated, true, null, null);
                    },
                    () -> show(stage, assetStore, ui, player, true, null, null)));
            table.add(change).height(96).padTop(34).colspan(2).row();
        } else if (onChallenge != null) {
            TextButton challenge = UiKit.button(assetStore, "CHALLENGE", UiKit.Style.PRIMARY, () -> {
                CommonGameFunctions.hideOverlayScreen(true);
                onChallenge.run();
            });
            table.add(challenge).width(340).height(100).padTop(34).colspan(2).row();
        }

        Image close = new Image(assetStore.otherComponents.findRegion("exit"));
        close.setSize(110, 110);
        close.setPosition(stage.getWidth() - close.getWidth() - 60, stage.getHeight() - close.getHeight() - 60);
        close.addListener(new ClickListener() {
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                close.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                CommonGameFunctions.hideOverlayScreen();
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                close.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                return true;
            }
        });

        table.pack();
        table.setPosition(stage.getWidth() / 2 - table.getWidth() / 2, stage.getHeight() / 2 - table.getHeight() / 2);
        overlay.addActor(table);
        overlay.addActor(close);
        CommonGameFunctions.showOverlay();
    }

    /**
     * Name shown to other players: nickname if set, otherwise their Google name.
     */
    public static String displayName(JsonObject player) {
        String nick = text(player, "nick_name", null);
        return nick != null && !nick.isEmpty() ? nick : text(player, "full_name", text(player, "name", "Player"));
    }

    private static void addRow(Table table, AssetStore assetStore, String label, String value) {
        Label name = GameText.label(assetStore.messageFont, label);
        name.setColor(LABEL_COLOR);
        table.add(name).right().padRight(40).padBottom(12);
        table.add(GameText.label(assetStore.messageFont, value)).left().padBottom(12).row();
    }

    private static String text(JsonObject player, String key, String fallback) {
        JsonElement value = player.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }
}
