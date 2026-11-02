package com.piga21.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import com.piga21.AssetStore;
import com.piga21.points.GamePoint;

/**
 * A stake chip with its name underneath ("STREET / HUSTLE"). The chip art has the name painted
 * below the coin; we only use the coin part and draw the name with the game fonts.
 */
public class ChipBadge extends Table {
    // Rows of the *_chip art taken up by the coin and its shadow; the painted name starts below
    private static final int COIN_ROWS = 253;
    private static final Color SELECTED_COLOR = Color.valueOf("#F4B331");

    public final Image coin;
    private final Label name;
    private final Label tagline;

    public ChipBadge(AssetStore assetStore, String key, float coinWidth) {
        TextureRegion region = coinRegion(assetStore, key);
        coin = new Image(region);
        float coinHeight = coinWidth * region.getRegionHeight() / region.getRegionWidth();
        coin.setSize(coinWidth, coinHeight);
        coin.setOrigin(coinWidth / 2, coinHeight / 2);

        name = GameText.label(assetStore.chipNameFont, nameOf(key));
        tagline = GameText.label(assetStore.chipTaglineFont, taglineOf(key));

        add(coin).size(coinWidth, coinHeight).row();
        add(name).padTop(-6).row();
        add(tagline).padTop(-4).row();
        pack();
    }

    public void setSelected(boolean selected) {
        name.setColor(selected ? SELECTED_COLOR : Color.WHITE);
        tagline.setColor(selected ? SELECTED_COLOR : Color.WHITE);
    }

    /**
     * Just the coin from a chip's art, without the painted name.
     */
    public static TextureRegion coinRegion(AssetStore assetStore, String key) {
        TextureRegion full = assetStore.otherComponents.findRegion(key + "_chip");
        return new TextureRegion(full, 0, 0, full.getRegionWidth(), Math.min(COIN_ROWS, full.getRegionHeight()));
    }

    /**
     * The chips players can pick, smallest first. Must match the server's stakes.
     */
    public static final String[] KEYS = {"street", "stakes", "boss", "legend", "royal"};

    /**
     * street / stakes / boss / legend / royal for a stake amount.
     */
    public static String keyForAmount(int amount) {
        return switch (amount) {
            case 500 -> "stakes";
            case 1000 -> "boss";
            case 2000 -> "legend";
            case 5000 -> "royal";
            default -> "street";
        };
    }

    public static int amountOf(String key) {
        return switch (key) {
            case "stakes" -> 500;
            case "boss" -> 1000;
            case "legend" -> 2000;
            case "royal" -> 5000;
            default -> 200;
        };
    }

    /**
     * The chip saved as the player's choice; one that no longer exists (the old 50 "rockie" or
     * 250 "casino") becomes the smallest chip.
     */
    public static String selectedKey() {
        String saved = Gdx.app.getPreferences(GamePoint.GAME_PREFS).getString("selected_chip", KEYS[0]);
        for (String key : KEYS) {
            if (key.equals(saved)) return key;
        }
        return KEYS[0];
    }

    private static String nameOf(String key) {
        return switch (key) {
            case "stakes" -> "STAKES";
            case "boss" -> "BOSS";
            case "legend" -> "LEGEND";
            case "royal" -> "ROYAL";
            default -> "STREET";
        };
    }

    private static String taglineOf(String key) {
        return switch (key) {
            case "stakes" -> "HYPE";
            case "boss" -> "BENCH";
            case "legend" -> "LEAGUE";
            case "royal" -> "FLUSH";
            default -> "HUSTLE";
        };
    }
}
