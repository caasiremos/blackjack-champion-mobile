package com.piga21.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

import java.util.HashMap;
import java.util.Map;

import com.piga21.AssetStore;

/**
 * Shared look for dialogs, buttons, text fields and list cards, drawn from code so every
 * screen matches: maroon panels with a gold border, gold primary buttons with dark red text
 * (like the button art), maroon outlined secondary buttons.
 */
public class UiKit {
    public enum Style { PRIMARY, SECONDARY, SMALL }

    public static final Color GOLD = Color.valueOf("#F4B331");
    public static final Color GOLD_DARK = Color.valueOf("#D08A12");
    public static final Color MAROON = Color.valueOf("#550A0A");
    public static final Color BUTTON_TEXT = Color.valueOf("#7A1600");
    public static final Color ERROR = Color.valueOf("#FF6B5E");
    public static final Color MUTED = Color.valueOf("#FFC9C0");

    private static final Map<String, NinePatchDrawable> cache = new HashMap<>();

    public static Drawable panel() {
        return padded(rounded("panel", new Color(0.33f, 0.04f, 0.04f, 0.97f), GOLD, 32, 4), 50, 40);
    }

    public static Drawable card() {
        return padded(rounded("card", new Color(1, 1, 1, 0.07f), new Color(1, 1, 1, 0.18f), 22, 2), 18, 10);
    }

    public static Drawable cardPressed() {
        return padded(rounded("cardPressed", new Color(0.96f, 0.7f, 0.19f, 0.28f), GOLD, 22, 2), 18, 10);
    }

    /**
     * A button that doesn't pass its taps on to whatever it sits in (e.g. a tappable row).
     */
    public static TextButton button(AssetStore assetStore, String text, Style style, Runnable onClick) {
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        switch (style) {
            case PRIMARY -> {
                buttonStyle.up = padded(rounded("primary", GOLD, Color.valueOf("#8A3A00"), 24, 4), 40, 14);
                buttonStyle.down = padded(rounded("primaryDown", GOLD_DARK, Color.valueOf("#8A3A00"), 24, 4), 40, 14);
                buttonStyle.disabled = padded(rounded("primaryDisabled", Color.valueOf("#8C7A55"), Color.valueOf("#5C4A2A"), 24, 4), 40, 14);
                buttonStyle.font = assetStore.buttonFont;
                buttonStyle.fontColor = BUTTON_TEXT;
                buttonStyle.disabledFontColor = Color.valueOf("#4A3A20");
            }
            case SECONDARY -> {
                buttonStyle.up = padded(rounded("secondary", MAROON, GOLD, 24, 4), 40, 14);
                buttonStyle.down = padded(rounded("secondaryDown", Color.valueOf("#7A1A1A"), GOLD, 24, 4), 40, 14);
                buttonStyle.font = assetStore.buttonFont;
                buttonStyle.fontColor = GOLD;
            }
            case SMALL -> {
                buttonStyle.up = padded(rounded("small", new Color(0.33f, 0.04f, 0.04f, 0.9f), GOLD, 18, 3), 22, 8);
                buttonStyle.down = padded(rounded("smallDown", Color.valueOf("#7A1A1A"), GOLD, 18, 3), 22, 8);
                buttonStyle.font = assetStore.smallButtonFont;
                buttonStyle.fontColor = GOLD;
            }
        }

        TextButton button = new TextButton(text, buttonStyle);
        button.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int b) {
                event.stop();
                return false;
            }
        });
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (!button.isDisabled()) onClick.run();
            }
        });
        return button;
    }

    public static TextField textField(AssetStore assetStore, String text, String placeholder) {
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = assetStore.inputFont;
        style.fontColor = Color.valueOf("#2E1A1A");
        style.messageFont = assetStore.inputFont;
        style.messageFontColor = Color.valueOf("#A88A8A");
        style.background = padded(rounded("field", Color.WHITE, GOLD, 20, 4), 28, 18);
        style.focusedBackground = padded(rounded("fieldFocused", Color.WHITE, Color.valueOf("#FFD36B"), 20, 5), 28, 18);
        NinePatchDrawable cursor = rounded("cursor", GOLD_DARK, null, 2, 0);
        cursor.setMinWidth(4);
        style.cursor = cursor;
        style.selection = rounded("selection", new Color(0.96f, 0.7f, 0.19f, 0.45f), null, 2, 0);

        TextField field = new TextField(text, style);
        field.setMessageText(placeholder);
        return field;
    }

    private static NinePatchDrawable padded(NinePatchDrawable drawable, float horizontal, float vertical) {
        NinePatchDrawable copy = new NinePatchDrawable(drawable);
        copy.setLeftWidth(horizontal);
        copy.setRightWidth(horizontal);
        copy.setTopHeight(vertical);
        copy.setBottomHeight(vertical);
        return copy;
    }

    /**
     * A rounded rectangle nine-patch, drawn at 4x and scaled down so the corners are smooth.
     */
    private static NinePatchDrawable rounded(String key, Color fill, Color border, int radius, int borderWidth) {
        NinePatchDrawable cached = cache.get(key);
        if (cached != null) return cached;

        int scale = 4;
        int size = radius * 2 + 2;
        int big = size * scale;
        Pixmap large = new Pixmap(big, big, Pixmap.Format.RGBA8888);
        large.setBlending(Pixmap.Blending.None);
        if (border != null && borderWidth > 0) {
            fillRounded(large, 0, 0, big, big, radius * scale, border);
            int inset = borderWidth * scale;
            fillRounded(large, inset, inset, big - 2 * inset, big - 2 * inset, Math.max(0, (radius - borderWidth) * scale), fill);
        } else {
            fillRounded(large, 0, 0, big, big, radius * scale, fill);
        }

        Pixmap small = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        small.setBlending(Pixmap.Blending.None);
        small.setFilter(Pixmap.Filter.BiLinear);
        small.drawPixmap(large, 0, 0, big, big, 0, 0, size, size);
        large.dispose();

        // Managed (keeps the pixmap) so it survives the GL context being lost on Android pause
        Texture texture = new Texture(new PixmapTextureData(small, null, false, false, true));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        NinePatchDrawable drawable = new NinePatchDrawable(new NinePatch(texture, radius, radius, radius, radius));
        cache.put(key, drawable);
        return drawable;
    }

    private static void fillRounded(Pixmap pixmap, int x, int y, int w, int h, int r, Color color) {
        pixmap.setColor(color);
        if (r <= 0) {
            pixmap.fillRectangle(x, y, w, h);
            return;
        }
        pixmap.fillRectangle(x + r, y, w - 2 * r, h);
        pixmap.fillRectangle(x, y + r, w, h - 2 * r);
        pixmap.fillCircle(x + r, y + r, r);
        pixmap.fillCircle(x + w - r - 1, y + r, r);
        pixmap.fillCircle(x + r, y + h - r - 1, r);
        pixmap.fillCircle(x + w - r - 1, y + h - r - 1, r);
    }
}
