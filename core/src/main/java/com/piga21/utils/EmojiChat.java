package com.piga21.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

import java.util.function.BiConsumer;

import com.piga21.AssetStore;

/**
 * Emoji the two players send each other during a game. A tray of five sits in a corner; a tapped
 * one pops up big with a little burst, and the opponent sees the same on their screen.
 */
public class EmojiChat {
    private static final float TRAY_SIZE = 92;
    private static final float TRAY_GAP = 20; // between neighbouring emoji
    private static final float BUBBLE_SIZE = 190;
    private static final float COOLDOWN_SECONDS = 1.2f; // the server passes on one a second at most

    private final Stage stage;
    private final AssetStore assetStore;
    private final Table tray = new Table();
    private Group mineShowing, theirsShowing;
    private float cooldownLeft = 0;

    /**
     * @param onSend gets the emoji's name and where in the stage it was tapped
     */
    public EmojiChat(Stage stage, AssetStore assetStore, BiConsumer<String, Vector2> onSend) {
        this.stage = stage;
        this.assetStore = assetStore;

        tray.setBackground(UiKit.pill());
        tray.pad(12, 18, 12, 18);
        for (String name : AssetStore.EMOJIS) {
            TextureRegion region = assetStore.emoji(name);
            if (region == null) continue;
            Image item = new Image(region);
            item.setSize(TRAY_SIZE, TRAY_SIZE);
            item.setOrigin(Align.center);
            item.addListener(new ClickListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    item.clearActions();
                    item.addAction(Actions.scaleTo(0.82f, 0.82f, 0.08f));
                    return super.touchDown(event, x, y, pointer, button);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    super.touchUp(event, x, y, pointer, button);
                    item.addAction(Actions.scaleTo(1f, 1f, 0.25f, Interpolation.swingOut));
                }

                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (cooldownLeft > 0) return;
                    startCooldown();
                    onSend.accept(name, item.localToStageCoordinates(new Vector2(TRAY_SIZE / 2, TRAY_SIZE / 2)));
                }
            });
            tray.add(item).size(TRAY_SIZE, TRAY_SIZE).pad(0, TRAY_GAP / 2, 0, TRAY_GAP / 2);
        }
        tray.pack();
        tray.setTransform(true); // so it can be shrunk to fit a narrow screen
        // A gentle nudge when the game starts, so players notice they can react
        tray.getColor().a = 0;
        tray.addAction(Actions.sequence(Actions.delay(0.6f), Actions.fadeIn(0.4f)));
    }

    public Table tray() {
        return tray;
    }

    /**
     * Put the tray in the bottom-right corner, all in one row, shrinking it if it would be
     * wider than {@code maxWidth} (e.g. to stay clear of the HIT / STAND buttons).
     */
    public void placeBottomRight(float stageWidth, float margin, float maxWidth) {
        float scale = Math.min(1f, maxWidth / tray.getWidth());
        tray.setScale(scale);
        tray.setPosition(stageWidth - tray.getWidth() * scale - margin, margin);
    }

    /** Call every frame */
    public void update(float delta) {
        if (cooldownLeft > 0) {
            cooldownLeft -= delta;
            if (cooldownLeft <= 0) {
                tray.setTouchable(Touchable.enabled);
                tray.addAction(Actions.alpha(1f, 0.15f));
            }
        }
    }

    public void hide() {
        tray.clearActions();
        tray.addAction(Actions.sequence(Actions.fadeOut(0.2f), Actions.visible(false)));
        tray.setTouchable(Touchable.disabled);
    }

    private void startCooldown() {
        cooldownLeft = COOLDOWN_SECONDS;
        tray.setTouchable(Touchable.disabled);
        tray.addAction(Actions.alpha(0.45f, 0.1f));
    }

    /**
     * Pop an emoji up at (x, y), its centre. From a tray tap it flies there from the tray.
     *
     * @param from where it starts (the tapped emoji), or null to appear in place
     * @param who  shown under it, e.g. "YOU" or the opponent's name
     */
    public void show(String name, boolean mine, String who, float x, float y, Vector2 from) {
        TextureRegion region = assetStore.emoji(name);
        if (region == null) return; // from a newer app with emoji this one doesn't have

        // One at a time on each side: a new one replaces the last
        Group previous = mine ? mineShowing : theirsShowing;
        if (previous != null) previous.remove();

        Group bubble = new Group();
        bubble.setSize(BUBBLE_SIZE, BUBBLE_SIZE);
        bubble.setOrigin(Align.center);
        bubble.setTouchable(Touchable.disabled);

        Image glow = new Image(UiKit.glow(mine ? "emojiGlowMine" : "emojiGlowTheirs",
            mine ? new Color(0.96f, 0.7f, 0.19f, 0.55f) : new Color(1f, 1f, 1f, 0.4f)));
        float glowSize = BUBBLE_SIZE * 1.9f;
        glow.setBounds((BUBBLE_SIZE - glowSize) / 2, (BUBBLE_SIZE - glowSize) / 2, glowSize, glowSize);
        bubble.addActor(glow);

        Image face = new Image(region);
        face.setSize(BUBBLE_SIZE, BUBBLE_SIZE);
        bubble.addActor(face);

        Label tag = GameText.label(assetStore.chipTaglineFont, who);
        tag.setColor(mine ? UiKit.GOLD : UiKit.INK);
        tag.setAlignment(Align.center);
        tag.setEllipsis(true);
        tag.setWidth(BUBBLE_SIZE * 1.6f);
        tag.setPosition(BUBBLE_SIZE / 2 - tag.getWidth() / 2, -tag.getPrefHeight() - 4);
        bubble.addActor(tag);

        float targetX = x - BUBBLE_SIZE / 2, targetY = y - BUBBLE_SIZE / 2;
        if (from != null) {
            bubble.setPosition(from.x - BUBBLE_SIZE / 2, from.y - BUBBLE_SIZE / 2);
        } else {
            bubble.setPosition(targetX, targetY);
        }
        bubble.setScale(from != null ? 0.45f : 0.2f);
        bubble.getColor().a = 0;
        stage.addActor(bubble);

        bubble.addAction(Actions.sequence(
            Actions.parallel(
                Actions.fadeIn(0.15f),
                Actions.moveTo(targetX, targetY, 0.4f, Interpolation.pow2Out),
                Actions.scaleTo(1f, 1f, 0.45f, Interpolation.swingOut)),
            Actions.run(() -> burst(region, x, y)),
            // A little wobble, like it's alive
            Actions.repeat(2, Actions.sequence(
                Actions.rotateBy(9, 0.11f, Interpolation.sine),
                Actions.rotateBy(-18, 0.22f, Interpolation.sine),
                Actions.rotateBy(9, 0.11f, Interpolation.sine))),
            Actions.delay(1.0f),
            Actions.parallel(
                Actions.moveBy(0, 70, 0.55f, Interpolation.pow2In),
                Actions.fadeOut(0.55f)),
            Actions.removeActor()));

        if (mine) mineShowing = bubble; else theirsShowing = bubble;
    }

    /** Small copies that pop out from the centre and fade */
    private void burst(TextureRegion region, float x, float y) {
        int count = 6;
        for (int i = 0; i < count; i++) {
            float angle = 360f / count * i + MathUtils.random(-18f, 18f);
            float distance = MathUtils.random(120f, 170f);
            float size = MathUtils.random(38f, 54f);
            Image bit = new Image(region);
            bit.setSize(size, size);
            bit.setOrigin(Align.center);
            bit.setPosition(x - size / 2, y - size / 2);
            bit.setTouchable(Touchable.disabled);
            bit.setScale(0.4f);
            stage.addActor(bit);
            bit.addAction(Actions.sequence(
                Actions.parallel(
                    Actions.moveBy(MathUtils.cosDeg(angle) * distance, MathUtils.sinDeg(angle) * distance, 0.6f, Interpolation.pow2Out),
                    Actions.scaleTo(1f, 1f, 0.3f),
                    Actions.rotateBy(MathUtils.random(-60f, 60f), 0.6f),
                    Actions.sequence(Actions.delay(0.25f), Actions.fadeOut(0.35f))),
                Actions.removeActor()));
        }
        // The bubble stays on top of its burst
        Group current = mineShowing != null && mineShowing.hasParent() ? mineShowing : null;
        if (current != null) current.toFront();
        if (theirsShowing != null && theirsShowing.hasParent()) theirsShowing.toFront();
    }
}
