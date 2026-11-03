package com.piga21;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import com.piga21.cards.Card;
import com.piga21.utils.MusicPlayer;

/**
 * Every texture, font and sound the game uses. Only what the splash screen needs is loaded
 * before the first frame ({@link #loadSplashAssets()}); the rest loads while the splash is
 * showing, through {@link #update()}. Fonts are the slow part (about two seconds), so their
 * glyphs are drawn on a background thread and only uploaded as textures on the render thread.
 */
public class AssetStore {
    public TextureAtlas redAtlas, blackAtlas, components, otherComponents, backgroundComponents;
    public Sound betchipSound, bell, blackJack, win, hitSound, shuffle, betChipInitializer;
    public HashMap<Card, TextureRegion> hashMap;
    // Lilita One for titles, buttons, counters and results; Fredoka for names, lists and messages (both SIL OFL)
    private static final String DISPLAY_FONT = "fonts/LilitaOne-Regular.ttf";
    private static final String TEXT_FONT = "fonts/Fredoka-SemiBold.ttf";
    private static final Color OUTLINE_COLOR = Color.valueOf("#2A1600");
    private static final Color SHADOW_COLOR = new Color(0f, 0f, 0f, 0.45f);
    private static final Color TITLE_OUTLINE_COLOR = new Color(0.12f, 0.03f, 0.03f, 0.9f);

    private static final String OTHER_COMPONENTS = "other_components.txt";
    private static final String RED_THEME = "themes/theme_red.txt";
    private static final String[] THEMES = {"themes/theme_red.txt", "themes/theme_blue.txt", "themes/theme_green.txt"};

    public BitmapFont font, dialogFont, counterFont, blackJackFont, dialogCounterFont, betAmountDialogFont, listFont;
    // Text that used to be painted into images: titles, ribbons, chip names, messages
    public BitmapFont heroFont, titleFont, ribbonFont, chipNameFont, chipTaglineFont, messageFont;
    // Plain white (no outline) so UI widgets can colour them: buttons and typed text
    public BitmapFont buttonFont, smallButtonFont, inputFont;
    // Clean (no outline or shadow) lobby type: big numbers, headings, names and captions
    public BitmapFont statFont, headingFont, valueFont, bodyFont, captionFont;
    // Small versions of the hero and tagline fonts with only the letters the splash screen shows
    public BitmapFont splashTitleFont, splashTextFont;
    public static final String SPLASH_TITLE = "PIGA21";
    public static final String[] SPLASH_TEXTS = {
        "B L A C K J A C K   ·   O N L I N E", "Loading...", "Signing you in...", "Tap anywhere to sign in with Google"};
    public MusicPlayer musicPlayer;

    private final AssetManager manager = new AssetManager();
    private String themePath;
    private final List<FontJob> fontJobs = new ArrayList<>();
    // Fonts whose glyphs are ready, waiting to be turned into textures on the render thread
    private final ConcurrentLinkedQueue<Runnable> fontUploads = new ConcurrentLinkedQueue<>();
    private int fontsDone = 0;
    private boolean loaded = false;
    private long loadStart;

    public AssetStore() {
        hashMap = new HashMap<>();
    }

    /**
     * The cards and the two fonts the splash screen draws, then starts loading everything else.
     */
    public void loadSplashAssets() {
        loadStart = System.currentTimeMillis();

        redAtlas = new TextureAtlas("cards/red_cards_atlas.txt");
        blackAtlas = new TextureAtlas("cards/black_cards_atlas.txt");
        mapCards();

        FreeTypeFontGenerator display = new FreeTypeFontGenerator(Gdx.files.internal(DISPLAY_FONT));
        FreeTypeFontGenerator text = new FreeTypeFontGenerator(Gdx.files.internal(TEXT_FONT));
        FreeTypeFontGenerator.FreeTypeFontParameter title = parameter(100, "#FFFFFF", 4f, 5, TITLE_OUTLINE_COLOR);
        title.characters = SPLASH_TITLE;
        splashTitleFont = display.generateFont(title);
        FreeTypeFontGenerator.FreeTypeFontParameter tagline = parameter(22, "#FFFFFF", 0f, 2, OUTLINE_COLOR);
        tagline.characters = String.join("", SPLASH_TEXTS);
        splashTextFont = text.generateFont(tagline);
        display.dispose();
        text.dispose();
        Gdx.app.log("AssetStore", "Splash assets ready in " + (System.currentTimeMillis() - loadStart) + " ms");

        // Textures and sounds: AssetManager decodes them off the render thread
        manager.load(OTHER_COMPONENTS, TextureAtlas.class);
        manager.load(RED_THEME, TextureAtlas.class);
        themePath = THEMES[new Random().nextInt(THEMES.length)];
        manager.load(themePath, TextureAtlas.class);
        for (String sound : new String[]{"bell", "win", "card_shuffle", "cardadding", "black_jack", "chip_initializer", "betchipsound"}) {
            manager.load("sounds/" + sound + ".mp3", Sound.class);
        }

        startFontThread();
    }

    /**
     * Moves loading along; call it every frame while the splash shows.
     *
     * @return true once everything is loaded
     */
    public boolean update() {
        if (loaded) return true;

        // Each upload is one font's texture pages: a few milliseconds
        Runnable upload;
        while ((upload = fontUploads.poll()) != null) upload.run();

        // A small time budget per frame keeps the splash animation smooth
        if (manager.update(12) && otherComponents == null) {
            otherComponents = manager.get(OTHER_COMPONENTS, TextureAtlas.class);
            backgroundComponents = manager.get(RED_THEME, TextureAtlas.class);
            components = manager.get(themePath, TextureAtlas.class);
            bell = sound("bell");
            win = sound("win");
            shuffle = sound("card_shuffle");
            hitSound = sound("cardadding");
            blackJack = sound("black_jack");
            betChipInitializer = sound("chip_initializer");
            betchipSound = sound("betchipsound");
            musicPlayer = new MusicPlayer();
            musicPlayer.initialize("sounds/background_music.mp3");
        }

        if (otherComponents != null && fontsDone == fontJobs.size()) {
            loaded = true;
            Gdx.app.log("AssetStore", "All assets ready in " + (System.currentTimeMillis() - loadStart) + " ms");
        }
        return loaded;
    }

    /**
     * How far loading has got, from 0 to 1, for the splash screen's bar.
     */
    public float progress() {
        if (loaded) return 1f;
        float fonts = fontJobs.isEmpty() ? 1f : (float) fontsDone / fontJobs.size();
        return 0.8f * fonts + 0.2f * manager.getProgress();
    }

    private Sound sound(String name) {
        return manager.get("sounds/" + name + ".mp3", Sound.class);
    }

    private void mapCards() {
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

    // ---- Fonts ----

    private record FontJob(String face, FreeTypeFontGenerator.FreeTypeFontParameter parameter, Consumer<BitmapFont> into) {
    }

    private void startFontThread() {
        font(DISPLAY_FONT, 100, "#FFFFFF", 4f, 5, TITLE_OUTLINE_COLOR, f -> heroFont = f);
        font(DISPLAY_FONT, 92, "#F4AD31", 5f, 5, OUTLINE_COLOR, f -> blackJackFont = f);
        font(DISPLAY_FONT, 64, "#FFFFFF", 3f, 4, TITLE_OUTLINE_COLOR, f -> titleFont = f);
        font(DISPLAY_FONT, 56, "#FFFFFF", 3f, 3, TITLE_OUTLINE_COLOR, f -> ribbonFont = f);
        font(DISPLAY_FONT, 46, "#F4B331", 3f, 3, OUTLINE_COLOR, f -> font = f);
        font(DISPLAY_FONT, 46, "#F4B331", 3f, 3, OUTLINE_COLOR, f -> dialogFont = f);
        font(DISPLAY_FONT, 40, "#FFFFFF", 0f, 0, OUTLINE_COLOR, f -> buttonFont = f);
        font(DISPLAY_FONT, 38, "#F4B331", 2.5f, 2, OUTLINE_COLOR, f -> counterFont = f);
        font(DISPLAY_FONT, 38, "#FFFFFF", 2.5f, 3, TITLE_OUTLINE_COLOR, f -> chipNameFont = f);
        font(DISPLAY_FONT, 30, "#FFFFFF", 2f, 2, OUTLINE_COLOR, f -> dialogCounterFont = f);
        font(DISPLAY_FONT, 30, "#EB2F23", 0f, 0, OUTLINE_COLOR, f -> betAmountDialogFont = f);
        font(DISPLAY_FONT, 28, "#FFFFFF", 0f, 0, OUTLINE_COLOR, f -> smallButtonFont = f);
        font(TEXT_FONT, 22, "#FFFFFF", 0f, 2, OUTLINE_COLOR, f -> chipTaglineFont = f);
        font(TEXT_FONT, 28, "#FFFFFF", 0f, 2, OUTLINE_COLOR, f -> listFont = f);
        font(TEXT_FONT, 36, "#FFFFFF", 0f, 2, OUTLINE_COLOR, f -> messageFont = f);
        font(TEXT_FONT, 40, "#FFFFFF", 0f, 0, OUTLINE_COLOR, f -> inputFont = f);
        font(DISPLAY_FONT, 104, "#FFFFFF", 0f, 0, OUTLINE_COLOR, f -> statFont = f);
        font(DISPLAY_FONT, 56, "#FFFFFF", 0f, 0, OUTLINE_COLOR, f -> headingFont = f);
        font(TEXT_FONT, 56, "#FFFFFF", 0f, 0, OUTLINE_COLOR, f -> valueFont = f);
        font(TEXT_FONT, 34, "#FFFFFF", 0f, 0, OUTLINE_COLOR, f -> bodyFont = f);
        font(TEXT_FONT, 24, "#FFFFFF", 0f, 0, OUTLINE_COLOR, f -> captionFont = f);

        // Three workers, each with its own generator (one isn't safe to share between threads):
        // the display fonts, the biggest and slowest, split between two, the text fonts on the third
        List<FontJob> displayA = new ArrayList<>(), displayB = new ArrayList<>(), textJobs = new ArrayList<>();
        for (FontJob job : fontJobs) {
            if (job.face.equals(TEXT_FONT)) textJobs.add(job);
            else (displayA.size() <= displayB.size() ? displayA : displayB).add(job);
        }
        startWorker(DISPLAY_FONT, displayA, "font-loader-1");
        startWorker(DISPLAY_FONT, displayB, "font-loader-2");
        startWorker(TEXT_FONT, textJobs, "font-loader-3");
    }

    private void startWorker(String face, List<FontJob> jobs, String name) {
        Thread thread = new Thread(() -> {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal(face));
            try {
                for (int i = 0; i < jobs.size(); i++) {
                    FontJob job = jobs.get(i);
                    try {
                        FreeTypeFontGenerator.FreeTypeBitmapFontData data = generator.generateData(job.parameter);
                        fontUploads.add(() -> {
                            data.regions = new Array<>();
                            job.parameter.packer.updateTextureRegions(data.regions, job.parameter.minFilter, job.parameter.magFilter, false);
                            job.into.accept(new BitmapFont(data, data.regions, true));
                            fontsDone++;
                        });
                    } catch (Throwable e) {
                        // Finish this worker's fonts on the render thread instead, the slow way
                        Gdx.app.error("AssetStore", "Background font loading failed", e);
                        List<FontJob> rest = new ArrayList<>(jobs.subList(i, jobs.size()));
                        fontUploads.add(() -> {
                            FreeTypeFontGenerator fallback = new FreeTypeFontGenerator(Gdx.files.internal(face));
                            for (FontJob job2 : rest) {
                                job2.parameter.packer = null;
                                job2.into.accept(fallback.generateFont(job2.parameter));
                                fontsDone++;
                            }
                            fallback.dispose();
                        });
                        return;
                    }
                }
            } finally {
                generator.dispose();
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Queues a font. Giving it its own packer keeps generateData off the GPU, so it can run on
     * the background thread; the packer's pages become textures in {@link #update()}.
     */
    private void font(String face, int size, String color, float outline, int shadow,
                      Color outlineColor, Consumer<BitmapFont> into) {
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = parameter(size, color, outline, shadow, outlineColor);

        // Same page size and settings the generator would pick for itself
        int lineHeight = (int) Math.ceil(size * 1.3f + outline * 2 + shadow);
        int pageSize = Math.min(1024, MathUtils.nextPowerOfTwo(
            (int) Math.sqrt((double) lineHeight * lineHeight * parameter.characters.length())));
        PixmapPacker packer = new PixmapPacker(pageSize, pageSize, Pixmap.Format.RGBA8888, 1, false, new PixmapPacker.SkylineStrategy());
        packer.setTransparentColor(outline > 0 ? outlineColor : parameter.color);
        packer.getTransparentColor().a = 0;
        parameter.packer = packer;

        fontJobs.add(new FontJob(face, parameter, into));
    }

    /**
     * @param outline dark outline width in pixels (0 for none), so text reads on busy backgrounds
     * @param shadow  drop shadow offset in pixels (0 for none)
     */
    private FreeTypeFontGenerator.FreeTypeFontParameter parameter(int fontSize, String color, float outline, int shadow, Color outlineColor) {
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
        return parameter;
    }

    public TextureRegion getTextureRegion(Card card) {
        return hashMap.get(card);
    }

    public Image redBackground() {
        Texture texture = new Texture(Gdx.files.internal("themes/red_background.png"));
        return new Image(texture);
    }
}
