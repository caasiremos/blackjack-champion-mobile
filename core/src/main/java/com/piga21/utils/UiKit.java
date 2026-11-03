package com.piga21.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

import java.util.HashMap;
import java.util.Locale;
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

    /**
     * Forgets the drawn textures. Call when the game starts: Android can keep the process (and
     * this cache) alive after the player exits, but the new start has a new GL context, where
     * the old textures draw as black. They aren't disposed, as their handles are no longer ours.
     */
    public static void reset() {
        cache.clear();
        discs.clear();
    }

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
     * A stake card on the chips screen; the selected one gets a gold frame.
     */
    public static Drawable stakeCard(boolean selected) {
        return selected
            ? padded(rounded("stakeSelected", new Color(0.96f, 0.7f, 0.19f, 0.16f), GOLD, 28, 4), 26, 22)
            : padded(rounded("stake", new Color(0.12f, 0.01f, 0.01f, 0.45f), new Color(1, 1, 1, 0.14f), 28, 2), 26, 22);
    }

    /**
     * A translucent panel grouping a list, e.g. the lobby's players and leaderboard.
     */
    public static Drawable section() {
        return padded(rounded("section", new Color(0.1f, 0.01f, 0.01f, 0.55f), new Color(1, 1, 1, 0.12f), 30, 2), 30, 26);
    }

    /**
     * Dark strip behind the top bar.
     */
    public static Drawable bar() {
        return rounded("bar", new Color(0.1f, 0.01f, 0.01f, 0.72f), null, 1, 0);
    }

    /**
     * A thin gold rule, e.g. under the top bar.
     */
    public static Drawable rule() {
        return rounded("rule", new Color(0.96f, 0.7f, 0.19f, 0.55f), null, 1, 0);
    }

    /**
     * Rounded chip for small pieces of status, like the points balance.
     */
    public static Drawable pill() {
        return padded(rounded("pill", new Color(0.18f, 0.02f, 0.02f, 0.88f), GOLD, 34, 3), 40, 12);
    }

    /**
     * Darkens the edges of the screen, drawn stretched over everything behind the content.
     */
    public static Drawable vignette() {
        NinePatchDrawable cached = cache.get("vignette");
        if (cached != null) return cached;

        int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float centre = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - centre) / centre, dy = (y - centre) / centre;
                float t = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) / 1.414f);
                // Clear in the middle, easing to dark at the corners
                float alpha = 0.85f * t * t * (3 - 2 * t);
                pixmap.setColor(0.05f, 0f, 0f, alpha);
                pixmap.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(new PixmapTextureData(pixmap, null, false, false, true));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        NinePatchDrawable drawable = new NinePatchDrawable(new NinePatch(texture, 0, 0, 0, 0));
        cache.put("vignette", drawable);
        return drawable;
    }

    /* Lobby: deep burgundy surfaces with restrained gold */

    public static final Color INK = Color.valueOf("#FBEDE8");       // primary text on dark surfaces
    public static final Color SOFT = Color.valueOf("#D7AFA9");      // secondary text, still high contrast
    public static final Color FAINT = new Color(1, 1, 1, 0.08f);    // hairlines
    public static final Color ONLINE = Color.valueOf("#43D98B");
    public static final Color SILVER = Color.valueOf("#D3D8E0");
    public static final Color BRONZE = Color.valueOf("#D88B52");

    /**
     * A dark glassy panel holding a list, e.g. online players and the leaderboard.
     */
    public static Drawable surface() {
        return padded(rounded("surface", new Color(0.16f, 0.02f, 0.05f, 0.62f), new Color(1, 1, 1, 0.08f), 36, 2), 34, 30);
    }

    /**
     * A row inside a surface; pressed and "this is you" versions below.
     */
    public static Drawable tile() {
        return padded(rounded("tile", new Color(1, 1, 1, 0.055f), new Color(1, 1, 1, 0.08f), 26, 2), 22, 12);
    }

    public static Drawable tilePressed() {
        return padded(rounded("tilePressed", new Color(0.7f, 0.07f, 0.16f, 0.42f), new Color(0.96f, 0.7f, 0.19f, 0.85f), 26, 2), 22, 12);
    }

    public static Drawable tileMe() {
        return padded(rounded("tileMe", new Color(0.96f, 0.7f, 0.19f, 0.12f), new Color(0.96f, 0.7f, 0.19f, 0.8f), 26, 2), 22, 12);
    }

    /**
     * The centre card with the player's stake: warm crimson with a fine gold edge.
     */
    public static Drawable heroCard() {
        return padded(rounded("heroCard", new Color(0.29f, 0.03f, 0.07f, 0.88f), new Color(0.96f, 0.7f, 0.19f, 0.55f), 44, 3), 52, 40);
    }

    /**
     * A small sunken box for one number, e.g. points available.
     */
    public static Drawable statTile() {
        return padded(rounded("statTile", new Color(0, 0, 0, 0.3f), new Color(1, 1, 1, 0.07f), 24, 2), 28, 16);
    }

    /**
     * An outlined capsule for short tags ("THIS WEEK", "YOU").
     */
    public static Drawable tag(String key, Color color) {
        return padded(rounded("tag" + key, new Color(color.r, color.g, color.b, 0.12f), new Color(color.r, color.g, color.b, 0.7f), 20, 2), 16, 4);
    }

    /**
     * A block under a podium place, tinted like its medal.
     */
    public static Drawable pedestal(String key, Color color) {
        return rounded("pedestal" + key, new Color(color.r, color.g, color.b, 0.16f), new Color(color.r, color.g, color.b, 0.55f), 22, 2);
    }

    /**
     * A vertical gradient, stretched to whatever it covers.
     */
    public static Drawable gradient(String key, Color top, Color bottom) {
        NinePatchDrawable cached = cache.get(key);
        if (cached != null) return cached;
        Pixmap pixmap = new Pixmap(4, 256, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        Color c = new Color();
        for (int y = 0; y < 256; y++) {
            c.set(top).lerp(bottom, y / 255f);
            pixmap.setColor(c);
            pixmap.drawLine(0, y, 3, y);
        }
        return store(key, pixmap);
    }

    /**
     * A soft round glow that fades to nothing at its edge, e.g. behind the stake chip.
     */
    public static Drawable glow(String key, Color color) {
        NinePatchDrawable cached = cache.get(key);
        if (cached != null) return cached;
        int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float centre = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = Math.min(1f, (float) Math.hypot(x - centre, y - centre) / centre);
                float t = 1 - d;
                pixmap.setColor(color.r, color.g, color.b, color.a * t * t);
                pixmap.drawPixel(x, y);
            }
        }
        return store(key, pixmap);
    }

    /**
     * A "<" chevron, for the back button.
     */
    public static Drawable chevronLeft(Color color) {
        NinePatchDrawable cached = cache.get("chevronLeft");
        if (cached != null) return cached;
        int size = 64;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(color);
        // Two thick strokes, drawn as runs of dots
        for (int i = 0; i <= 40; i++) {
            float t = i / 40f;
            pixmap.fillCircle((int) (40 - 18 * t), (int) (12 + 20 * t), 5);
            pixmap.fillCircle((int) (40 - 18 * t), (int) (52 - 20 * t), 5);
        }
        return store("chevronLeft", pixmap);
    }

    private static final Map<String, TextureRegionDrawable> discs = new HashMap<>();

    /**
     * A ringed circle that stays round at any size (a nine-patch circle breaks when drawn small).
     *
     * @param ring ring thickness as a share of the radius
     */
    public static Drawable disc(String key, Color fill, Color ring, float ringShare) {
        TextureRegionDrawable cached = discs.get(key);
        if (cached != null) return cached;
        int size = 256;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float centre = (size - 1) / 2f, radius = size / 2f - 1, inner = radius * (1 - ringShare);
        Color c = new Color();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.hypot(x - centre, y - centre);
                // One pixel of soft edge on the outside and where the ring meets the fill
                float outer = Math.max(0, Math.min(1, radius - d + 0.5f));
                float ringMix = ring == null ? 0 : Math.max(0, Math.min(1, d - inner + 0.5f));
                c.set(fill).lerp(ring == null ? fill : ring, ringMix);
                c.a *= outer;
                pixmap.setColor(c);
                pixmap.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(new PixmapTextureData(pixmap, null, false, false, true));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        TextureRegionDrawable drawable = new TextureRegionDrawable(new TextureRegion(texture));
        discs.put(key, drawable);
        return drawable;
    }

    private static NinePatchDrawable store(String key, Pixmap pixmap) {
        Texture texture = new Texture(new PixmapTextureData(pixmap, null, false, false, true));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        NinePatchDrawable drawable = new NinePatchDrawable(new NinePatch(texture, 0, 0, 0, 0));
        cache.put(key, drawable);
        return drawable;
    }

    /**
     * A player's initial in a ringed circle, any size; optionally with a green "online" dot.
     *
     * @param ring the ring colour, e.g. gold for you or a medal colour on the podium
     */
    public static Stack avatar(AssetStore assetStore, String name, float size, Color ring, boolean filled, boolean online) {
        Stack avatar = new Stack();
        String key = "avatar" + ring + filled;
        avatar.add(new Image(disc(key, filled ? ring : new Color(0.24f, 0.03f, 0.07f, 1f), ring, 0.08f)));
        Label initial = GameText.label(size >= 100 ? assetStore.headingFont : assetStore.buttonFont,
            name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.US));
        initial.setColor(filled ? BUTTON_TEXT : ring);
        initial.setAlignment(Align.center);
        avatar.add(initial);
        if (online) {
            // Bottom right, ringed in the surface colour so it reads against the avatar
            Image dot = new Image(disc("onlineDot", ONLINE, new Color(0.1f, 0.01f, 0.03f, 1f), 0.3f));
            Table corner = new Table();
            corner.bottom().right();
            corner.add(dot).size(size * 0.3f, size * 0.3f);
            avatar.add(corner);
        }
        return avatar;
    }

    /**
     * A player's initial in a small gold circle (72 across), as in the lobby and at the table.
     */
    public static Stack initialBadge(AssetStore assetStore, String name) {
        Stack badge = new Stack();
        badge.add(new Image(circle("avatarSmall", GOLD, GOLD_DARK, 36, 4)));
        Label initial = GameText.label(assetStore.buttonFont, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.US));
        initial.setColor(BUTTON_TEXT);
        initial.setAlignment(Align.center);
        badge.add(initial);
        return badge;
    }

    /**
     * A filled circle with a ring, e.g. the player's initial.
     */
    public static Drawable circle(String key, Color fill, Color ring, int radius, int ringWidth) {
        return rounded(key, fill, ring, radius, ringWidth);
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
