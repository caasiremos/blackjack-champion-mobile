package com.piga21.utils;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

import com.piga21.AssetStore;

/**
 * Settings as an overlay: game sounds, sharing the game, and a way to the player's profile.
 */
public class SettingsDialog {

    /**
     * @param onProfile opens the player's profile; the dialog closes first
     */
    public static void show(Stage stage, AssetStore assetStore, CommonUtilInterface ui, Runnable onProfile) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.8f);

        Table table = new Table();
        table.setBackground(UiKit.panel());
        table.add(GameText.title(assetStore, "SETTINGS")).colspan(2).padBottom(36).row();

        TextButton[] sound = new TextButton[1];
        sound[0] = UiKit.button(assetStore, soundText(), UiKit.Style.SMALL, () -> {
            Settings.toggleSoundSettings(!Settings.getSoundSetting());
            sound[0].setText(soundText());
        });
        addRow(table, assetStore, "Game sounds", "Cards, chips and wins", sound[0]);

        addRow(table, assetStore, "My profile", "Name, nickname and record",
            UiKit.button(assetStore, "OPEN", UiKit.Style.SMALL, () -> {
                CommonGameFunctions.hideOverlayScreen(true);
                onProfile.run();
            }));

        addRow(table, assetStore, "Invite friends", "Share Piga21 and play them online",
            UiKit.button(assetStore, "SHARE", UiKit.Style.SMALL, () ->
                ui.shareApp("Play Piga21 with me! Blackjack against real players online.", "Piga21")));

        table.add(UiKit.button(assetStore, "DONE", UiKit.Style.PRIMARY, CommonGameFunctions::hideOverlayScreen))
            .colspan(2).width(360).height(110).padTop(40).row();

        table.pack();
        table.setPosition(stage.getWidth() / 2 - table.getWidth() / 2, stage.getHeight() / 2 - table.getHeight() / 2);
        overlay.addActor(table);
        CommonGameFunctions.showOverlay();
    }

    private static String soundText() {
        return Settings.getSoundSetting() ? "ON" : "OFF";
    }

    private static void addRow(Table table, AssetStore assetStore, String title, String hint, TextButton action) {
        Table text = new Table();
        text.left();
        text.add(GameText.label(assetStore.messageFont, title)).left().row();
        Label hintLabel = GameText.label(assetStore.chipTaglineFont, hint);
        hintLabel.setColor(UiKit.MUTED);
        text.add(hintLabel).left();

        table.add(text).left().width(620).padBottom(26);
        table.add(action).right().width(190).height(76).padBottom(26).row();
    }
}
