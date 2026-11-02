package com.piga21.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.function.Consumer;

import com.piga21.AssetStore;

/**
 * A player's profile as an overlay: names, rank and record. The player's own profile also
 * shows their email and chips and lets them change their nickname; another player's never
 * includes an email (the server doesn't send it) and offers a challenge instead.
 */
public class ProfileDialog {
    private static final Color LABEL_COLOR = Color.valueOf("#FFC9C0");
    private static final Color LINK_COLOR = Color.valueOf("#F4B331");

    /**
     * @param own         true for the signed-in player's own profile
     * @param status      e.g. "Online", "In a game", or null
     * @param onChallenge shown as a Challenge button for other players; null hides it
     */
    public static void show(Stage stage, AssetStore assetStore, CommonUtilInterface ui, JsonObject player,
                            boolean own, String status, Runnable onChallenge) {
        show(stage, assetStore, ui, player, own, status, onChallenge, null);
    }

    /**
     * @param onUpdated gets the player's profile after they change their nickname; may be null
     */
    public static void show(Stage stage, AssetStore assetStore, CommonUtilInterface ui, JsonObject player,
                            boolean own, String status, Runnable onChallenge, Consumer<JsonObject> onUpdated) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.85f);

        Table table = new Table();
        table.setBackground(UiKit.panel());

        table.add(initialBadge(assetStore, displayName(player))).size(130, 130).padBottom(10).colspan(2).row();
        table.add(GameText.title(assetStore, displayName(player))).padBottom(status != null ? 4 : 30).colspan(2).row();
        if (status != null) {
            Label statusLabel = GameText.label(assetStore.listFont, status);
            statusLabel.setColor(LINK_COLOR);
            table.add(statusLabel).padBottom(30).colspan(2).row();
        }

        addRow(table, assetStore, "Name", text(player, "full_name", "-"));
        // No nickname means other players see the name, so show that
        addRow(table, assetStore, "Nickname", displayName(player));
        if (own) {
            addRow(table, assetStore, "Email", text(player, "email", "-"));
        }
        addRow(table, assetStore, "Rank", player.has("rank") && !player.get("rank").isJsonNull()
            ? "#" + player.get("rank").getAsInt() : "Unranked");
        addRow(table, assetStore, "Points won", count(player, "total_points"));
        addRow(table, assetStore, "Record", "Won " + text(player, "total_wins", "0")
            + "  ·  Lost " + text(player, "total_losses", "0")
            + "  ·  Tied " + text(player, "total_draws", "0"));

        if (own) {
            TextButton change = UiKit.button(assetStore, "CHANGE NICKNAME", UiKit.Style.SECONDARY, () ->
                NicknameDialog.show(stage, assetStore, text(player, "nick_name", ""), text(player, "full_name", ""),
                    updated -> {
                        String nick = text(updated, "nick_name", "");
                        ui.showToast(nick.isEmpty() ? "Nickname cleared, your name is shown" : "Nickname saved");
                        if (onUpdated != null) onUpdated.accept(updated);
                        show(stage, assetStore, ui, updated, true, null, null, onUpdated);
                    },
                    () -> show(stage, assetStore, ui, player, true, null, null, onUpdated)));
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

    /**
     * The first letter of the name in a gold circle (130 across), as in the main menu.
     */
    public static Stack initialBadge(AssetStore assetStore, String name) {
        Stack badge = new Stack();
        badge.add(new Image(UiKit.circle("avatarBadgeLarge", UiKit.GOLD, UiKit.GOLD_DARK, 65, 6)));
        Label initial = GameText.label(assetStore.titleFont, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.US));
        initial.setColor(UiKit.BUTTON_TEXT);
        initial.setAlignment(Align.center);
        badge.add(initial);
        return badge;
    }

    private static String count(JsonObject player, String key) {
        JsonElement value = player.get(key);
        return value == null || value.isJsonNull() ? "0" : String.format(Locale.US, "%,d", value.getAsLong());
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
