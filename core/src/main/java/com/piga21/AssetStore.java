package com.piga21;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Array;

import java.util.HashMap;
import java.util.Random;

import com.piga21.cards.Card;
import com.piga21.utils.MusicPlayer;

public class AssetStore {
    public TextureAtlas redAtlas, blackAtlas, components, otherComponents, backgroundComponents;
    public Sound betchipSound, bell, blackJack, win, hitSound, shuffle, betChipInitializer;
    public HashMap<Card, TextureRegion> hashMap;
    // Lilita One for titles, buttons, counters and results; Fredoka for names, lists and messages (both SIL OFL)
    private static final String DISPLAY_FONT = "fonts/LilitaOne-Regular.ttf";
    private static final String TEXT_FONT = "fonts/Fredoka-SemiBold.ttf";
    private static final Color OUTLINE_COLOR = Color.valueOf("#2A1600");
    private static final Color SHADOW_COLOR = new Color(0f, 0f, 0f, 0.45f);
    private static final Color TITLE_OUTLINE_COLOR = new Color(0.05f, 0.05f, 0.12f, 0.9f);

    public BitmapFont font, dialogFont, counterFont, blackJackFont, dialogCounterFont, betAmountDialogFont, listFont;
    // Text that used to be painted into images: titles, ribbons, chip names, messages
    public BitmapFont heroFont, titleFont, ribbonFont, chipNameFont, chipTaglineFont, messageFont;
    // Plain white (no outline) so UI widgets can colour them: buttons and typed text
    public BitmapFont buttonFont, smallButtonFont, inputFont;
    public MusicPlayer musicPlayer;


    public AssetStore() {
        hashMap = new HashMap<>();
    }

    public void loadAssets() {
        //load sounds
        bell = Gdx.audio.newSound(Gdx.files.internal("sounds/bell.mp3"));
        win = Gdx.audio.newSound(Gdx.files.internal("sounds/win.mp3"));
        shuffle = Gdx.audio.newSound(Gdx.files.internal("sounds/card_shuffle.mp3"));
        hitSound = Gdx.audio.newSound(Gdx.files.internal("sounds/cardadding.mp3"));
        blackJack = Gdx.audio.newSound(Gdx.files.internal("sounds/black_jack.mp3"));
        betChipInitializer = Gdx.audio.newSound(Gdx.files.internal("sounds/chip_initializer.mp3"));
        betchipSound = Gdx.audio.newSound(Gdx.files.internal("sounds/betchipsound.mp3"));

        musicPlayer = new MusicPlayer();
        musicPlayer.initialize("sounds/background_music.mp3");

        //load components
        redAtlas = new TextureAtlas("cards/red_cards_atlas.txt");
        blackAtlas = new TextureAtlas("cards/black_cards_atlas.txt");
        otherComponents = new TextureAtlas("other_components.txt");
        backgroundComponents = loadBlueTheme();
        components = loadRandomTheme(components);

        //load fonts: each file is read once and released after the sizes are generated
        FreeTypeFontGenerator display = new FreeTypeFontGenerator(Gdx.files.internal(DISPLAY_FONT));
        FreeTypeFontGenerator text = new FreeTypeFontGenerator(Gdx.files.internal(TEXT_FONT));

        listFont = loadFont(text, 28, "#FFFFFF", 0f, 2);
        font = loadFont(display, 46, "#F4B331", 3f, 3);
        dialogFont = loadFont(display, 46, "#F4B331", 3f, 3);
        blackJackFont = loadFont(display, 92, "#F4AD31", 5f, 5);
        counterFont = loadFont(display, 38, "#F4B331", 2.5f, 2);
        dialogCounterFont = loadFont(display, 30, "#FFFFFF", 2f, 2);
        betAmountDialogFont = loadFont(display, 30, "#EB2F23", 0f, 0);

        heroFont = loadFont(display, 100, "#FFFFFF", 4f, 5, TITLE_OUTLINE_COLOR);
        titleFont = loadFont(display, 64, "#FFFFFF", 3f, 4, TITLE_OUTLINE_COLOR);
        ribbonFont = loadFont(display, 56, "#FFFFFF", 3f, 3, TITLE_OUTLINE_COLOR);
        chipNameFont = loadFont(display, 38, "#FFFFFF", 2.5f, 3, TITLE_OUTLINE_COLOR);
        chipTaglineFont = loadFont(text, 22, "#FFFFFF", 0f, 2);
        messageFont = loadFont(text, 36, "#FFFFFF", 0f, 2);
        buttonFont = loadFont(display, 40, "#FFFFFF", 0f, 0);
        smallButtonFont = loadFont(display, 28, "#FFFFFF", 0f, 0);
        inputFont = loadFont(text, 40, "#FFFFFF", 0f, 0);

        display.dispose();
        text.dispose();
        // Load card textures
        for (Card c : Card.values()) {
            TextureRegion textureRegion;

            if (c.ordinal() >= 26) {
                textureRegion = redAtlas.findRegion(c.name());
            } else {
                textureRegion = blackAtlas.findRegion(c.name());
            }

            // Check if textureRegion is null
            if (textureRegion == null) {
                System.err.println("Warning: TextureRegion for card " + c.name() + " not found in atlas.");
            }

            // Store the region name and texture region in the hashmap
            hashMap.put(c, textureRegion);
        }
    }

    /**
     * @param outline dark outline width in pixels (0 for none), so text reads on busy backgrounds
     * @param shadow drop shadow offset in pixels (0 for none)
     */
    private BitmapFont loadFont(FreeTypeFontGenerator generator, int fontSize, String color, float outline, int shadow) {
        return loadFont(generator, fontSize, color, outline, shadow, OUTLINE_COLOR);
    }

    private BitmapFont loadFont(FreeTypeFontGenerator generator, int fontSize, String color, float outline, int shadow, Color outlineColor) {
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = fontSize; // Font size in pixels
        parameter.color = Color.valueOf(color); // Font color
        parameter.borderWidth = outline;
        parameter.borderColor = outlineColor;
        parameter.shadowOffsetX = shadow;
        parameter.shadowOffsetY = shadow;
        parameter.shadowColor = SHADOW_COLOR;
        // Labels get scaled in animations; linear filtering keeps edges smooth
        parameter.minFilter = Texture.TextureFilter.Linear;
        parameter.magFilter = Texture.TextureFilter.Linear;
        return generator.generateFont(parameter);
    }

    public TextureRegion getTextureRegion(Card card) {
        return hashMap.get(card);
    }

    private TextureAtlas loadRandomTheme(TextureAtlas components) {
        Random random = new Random();
        Array<String> themePaths = new Array<String>(
            new String[]{
                "themes/theme_red.txt",
                "themes/theme_blue.txt",
                "themes/theme_green.txt"
            });

        if (components != null) {
            components.dispose();
        }

        // Pick a random index
        int index = random.nextInt(themePaths.size);
        String selectedThemePath = themePaths.get(index);

        // Load the theme
        return new TextureAtlas(selectedThemePath);
    }

    private TextureAtlas loadBlueTheme(){
        return new TextureAtlas("themes/theme_blue.txt");
    }

    public Image blueBackground() {
        Texture texture = new Texture(Gdx.files.internal("themes/blue_background.png"));
        return new Image(texture);
    }
}
