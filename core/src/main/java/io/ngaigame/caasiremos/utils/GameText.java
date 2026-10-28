package io.ngaigame.caasiremos.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;

import io.ngaigame.caasiremos.AssetStore;

/**
 * Text that used to be painted into images, drawn with the game fonts instead.
 */
public class GameText {
    // Centre of the ribbon on the win/lose dialog art, as a fraction of the dialog size (from bottom-left)
    private static final float RIBBON_CENTRE_X = 0.5f;
    private static final float RIBBON_CENTRE_Y = 0.772f;

    public static Label label(BitmapFont font, String text) {
        Label label = new Label(text, new Label.LabelStyle(font, Color.WHITE));
        label.pack();
        return label;
    }

    /**
     * Section heading, e.g. "ONLINE GAMERS".
     */
    public static Label title(AssetStore assetStore, String text) {
        return label(assetStore.titleFont, text);
    }

    /**
     * "WIN" / "LOSE" on the dialog's ribbon. Call after the dialog is sized and positioned.
     */
    public static Label addRibbonText(AssetStore assetStore, Group parent, Image dialog, String text) {
        Label label = label(assetStore.ribbonFont, text);
        label.setPosition(
            dialog.getX() + dialog.getWidth() * RIBBON_CENTRE_X - label.getWidth() / 2,
            dialog.getY() + dialog.getHeight() * RIBBON_CENTRE_Y - label.getHeight() / 2
        );
        parent.addActor(label);
        return label;
    }
}
