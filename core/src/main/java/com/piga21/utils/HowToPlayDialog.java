package com.piga21.utils;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

import com.piga21.AssetStore;

/**
 * How a game of Piga21 is played, opened from the help button on the stakes screen.
 * The rules here follow the server's (HeadsUpBlackjack): hit or stand only, 30-second turns.
 */
public class HowToPlayDialog {

    private static final float COLUMN_WIDTH = 700;

    public static void show(Stage stage, AssetStore assetStore) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.82f);

        Table panel = new Table();
        panel.setBackground(UiKit.panel());
        panel.add(GameText.title(assetStore, "HOW TO PLAY")).padBottom(8).row();
        Label intro = GameText.label(assetStore.bodyFont, "Blackjack, one on one, against real players");
        intro.setColor(UiKit.SOFT);
        panel.add(intro).padBottom(24).row();

        // Two columns: playing a game, then how it's won and what it costs
        Table play = new Table();
        play.top().left();
        heading(play, assetStore, "PLAYING A GAME");
        step(play, assetStore, "1", "Pick your stake", "Players challenge you for your stake. You can challenge anyone online for theirs.");
        step(play, assetStore, "2", "Send a challenge", "Tap a player who's online. They have 10 seconds to accept.");
        step(play, assetStore, "3", "Get your cards", "You each get two. One of your opponent's stays face down until the end.");
        step(play, assetStore, "4", "Hit or stand", "Hit for another card, or Stand to keep your total. The challenger goes first. Take longer than 30 seconds and you stand.");

        Table win = new Table();
        win.top().left();
        heading(win, assetStore, "WINNING");
        point(win, assetStore, "Get closer to 21 than your opponent without going over.");
        point(win, assetStore, "Over 21 is a bust: you lose straight away.");
        point(win, assetStore, "21 with your first two cards is Blackjack and wins at once.");
        point(win, assetStore, "2 to 10 count as numbered, Jack, Queen and King are 10, and an Ace is 11 or 1.");
        heading(win, assetStore, "POINTS");
        point(win, assetStore, String.format(java.util.Locale.US,
            "The winner takes both stakes, less a few service points (%,d on a %,d stake).", ChipBadge.feeFor(200), 200));
        point(win, assetStore, "A draw gives you both all your points back.");
        point(win, assetStore, "Leaving a game, or staying disconnected for 20 seconds, loses it.");

        Table columns = new Table();
        columns.top();
        columns.add(play).width(COLUMN_WIDTH).top().padRight(60);
        columns.add(win).width(COLUMN_WIDTH).top();

        ScrollPane scroll = new ScrollPane(columns);
        scroll.setScrollingDisabled(true, false);
        scroll.setFadeScrollBars(false);
        scroll.setOverscroll(false, false);
        float maxHeight = stage.getHeight() - 420;
        panel.add(scroll).width(COLUMN_WIDTH * 2 + 60).height(Math.min(maxHeight, columns.getPrefHeight())).row();

        panel.add(UiKit.button(assetStore, "GOT IT", UiKit.Style.PRIMARY, CommonGameFunctions::hideOverlayScreen))
            .width(360).height(104).padTop(24).row();

        panel.pack();
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() / 2 - panel.getHeight() / 2);
        overlay.addActor(panel);
        CommonGameFunctions.showOverlay();
        stage.setScrollFocus(scroll);
    }

    private static void heading(Table table, AssetStore assetStore, String text) {
        Label label = GameText.label(assetStore.chipTaglineFont, text);
        label.setColor(UiKit.GOLD);
        table.add(label).colspan(2).left().padTop(table.hasChildren() ? 18 : 0).padBottom(12).row();
    }

    /** A numbered step: a gold disc with the number, a bold title and a line of detail. */
    private static void step(Table table, AssetStore assetStore, String number, String title, String detail) {
        Label digit = GameText.label(assetStore.smallButtonFont, number);
        digit.setColor(UiKit.BUTTON_TEXT);
        digit.setAlignment(Align.center);
        Table disc = new Table();
        disc.setBackground(UiKit.circle("helpStep", UiKit.GOLD, UiKit.GOLD_DARK, 26, 3));
        disc.add(digit);

        Table text = new Table();
        text.left();
        text.add(GameText.label(assetStore.messageFont, title)).left().row();
        text.add(wrapped(assetStore, detail, COLUMN_WIDTH - 76)).width(COLUMN_WIDTH - 76).left().padTop(2);

        table.add(disc).size(52, 52).top().left().padRight(24).padTop(2);
        table.add(text).width(COLUMN_WIDTH - 76).left().padBottom(20).row();
    }

    /** A bullet point: a small gold dot and a wrapped line. */
    private static void point(Table table, AssetStore assetStore, String text) {
        point(table, assetStore, text, COLUMN_WIDTH);
    }

    private static void point(Table table, AssetStore assetStore, String text, float width) {
        Table dot = new Table();
        dot.setBackground(UiKit.circle("helpDot", UiKit.GOLD, UiKit.GOLD, 6, 0));
        table.add(dot).size(12, 12).top().left().padTop(14).padRight(20);
        table.add(wrapped(assetStore, text, width - 32)).width(width - 32).left().padBottom(12).row();
    }

    private static Label wrapped(AssetStore assetStore, String text, float width) {
        Label label = GameText.label(assetStore.listFont, text);
        label.setColor(UiKit.INK);
        label.setWrap(true);
        label.setWidth(width);
        return label;
    }
}
