package com.piga21.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import com.piga21.AssetStore;
import com.piga21.Piga21Game;
import com.piga21.auth.GoogleSignInInterface;
import com.piga21.cards.Card;
import com.piga21.sockets.WebSocketHandler;
import com.piga21.utils.CommonUtilInterface;
import com.piga21.utils.GameText;
import com.piga21.utils.UiKit;

/**
 * Shown at launch: an ace and a king fan out (21), the title and a loading bar fade in. The
 * rest of the game's assets load meanwhile, and once they're in and the player is signed in
 * (but not before two seconds) it moves on to the main menu.
 */
public class SplashScreen implements Screen {
    private static final float SPLASH_SECONDS = 2f;
    private static final float CARD_WIDTH = 230, CARD_HEIGHT = 320;
    private static final float BAR_WIDTH = 520, BAR_HEIGHT = 8;

    Game game;
    ExtendViewport extendViewport;

    AssetStore assetStore;
    WebSocketHandler webSocketHandler;
    CommonUtilInterface commonUtilInterface;
    Stage stage;

    private final Label statusLabel;
    private final Image barFill;

    private float elapsed = 0f;
    private boolean signInRequested = false;
    private boolean leaving = false;

    public SplashScreen(Game game, AssetStore assetStore, ExtendViewport extendViewport,
                        WebSocketHandler webSocketHandler,
                        CommonUtilInterface commonUtilInterface) {
        stage = new Stage(extendViewport);
        this.game = game;
        this.assetStore = assetStore;
        this.webSocketHandler = webSocketHandler;
        this.commonUtilInterface = commonUtilInterface;
        this.extendViewport = extendViewport;

        float width = stage.getWidth(), height = stage.getHeight();
        float centreX = width / 2;

        Image background = assetStore.redBackground();
        background.setSize(width, height);
        Image vignette = new Image(UiKit.vignette());
        vignette.setSize(width, height);
        vignette.setTouchable(Touchable.disabled);
        stage.addActor(background);
        stage.addActor(vignette);

        // Ace and king: blackjack, the 21 in the name. They rise from below and fan out.
        float cardsY = height * 0.57f;
        addCard(assetStore, Card.ACE_SPADE, centreX - 70, cardsY, 12f, 0f);
        addCard(assetStore, Card.KING_HEART, centreX + 70, cardsY, -12f, 0.12f);

        // Title with the tagline between thin gold rules
        Label title = GameText.label(assetStore.splashTitleFont, AssetStore.SPLASH_TITLE);
        title.setColor(UiKit.GOLD);
        Label tagline = GameText.label(assetStore.splashTextFont, AssetStore.SPLASH_TEXTS[0]);
        tagline.setColor(UiKit.MUTED);
        Table taglineRow = new Table();
        taglineRow.add(new Image(UiKit.rule())).width(90).height(2).padRight(22);
        taglineRow.add(tagline);
        taglineRow.add(new Image(UiKit.rule())).width(90).height(2).padLeft(22);

        Table titleBlock = new Table();
        titleBlock.add(title).row();
        titleBlock.add(taglineRow).padTop(4);
        titleBlock.pack();
        titleBlock.setPosition(centreX - titleBlock.getWidth() / 2, height * 0.2f);
        titleBlock.getColor().a = 0f;
        titleBlock.addAction(Actions.sequence(Actions.delay(0.55f), Actions.fadeIn(0.6f, Interpolation.smooth)));
        stage.addActor(titleBlock);

        // Loading bar: a thin track that fills while the splash is up
        float barY = height * 0.12f;
        Image barTrack = new Image(UiKit.circle("barTrack", new Color(1, 1, 1, 0.12f), null, 4, 0));
        barTrack.setBounds(centreX - BAR_WIDTH / 2, barY, BAR_WIDTH, BAR_HEIGHT);
        barFill = new Image(UiKit.circle("barFill", UiKit.GOLD, null, 4, 0));
        barFill.setBounds(barTrack.getX(), barY, BAR_HEIGHT, BAR_HEIGHT);
        statusLabel = GameText.label(assetStore.splashTextFont, AssetStore.SPLASH_TEXTS[1]);
        statusLabel.setColor(UiKit.MUTED);
        statusLabel.setAlignment(Align.center);
        statusLabel.setWidth(BAR_WIDTH);
        statusLabel.setPosition(centreX - BAR_WIDTH / 2, barY - statusLabel.getHeight() - 14);
        for (Actor actor : new Actor[]{barTrack, barFill, statusLabel}) {
            actor.getColor().a = 0f;
            actor.addAction(Actions.sequence(Actions.delay(0.9f), Actions.fadeIn(0.4f)));
            stage.addActor(actor);
        }

        // If sign-in was dismissed, a tap anywhere brings the account picker back
        background.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (signInRequested) requestSignIn();
            }
        });
    }

    /**
     * A card that rises into place, turning to its angle in the fan.
     */
    private void addCard(AssetStore assetStore, Card card, float centreX, float centreY, float angle, float delay) {
        Image image = new Image(assetStore.getTextureRegion(card));
        image.setSize(CARD_WIDTH, CARD_HEIGHT);
        image.setOrigin(CARD_WIDTH / 2, 0);
        image.setPosition(centreX - CARD_WIDTH / 2, centreY - CARD_HEIGHT / 2 - 160);
        image.getColor().a = 0f;
        image.addAction(Actions.sequence(
            Actions.delay(delay),
            Actions.parallel(
                Actions.fadeIn(0.45f),
                Actions.moveBy(0, 160, 0.7f, Interpolation.pow3Out),
                Actions.rotateTo(angle, 0.7f, Interpolation.pow3Out))));
        stage.addActor(image);
    }

    /**
     * Online play needs a server account, so the menu only opens once the player is signed in.
     */
    private void continueToLobby() {
        GoogleSignInInterface auth = ((Piga21Game) game).getAuthInterface();
        if (auth != null && !auth.isSignedIn()) {
            if (!signInRequested) requestSignIn();
            return;
        }

        leaving = true;
        // Replace rather than push: back from the menu exits instead of returning to the splash
        ((Piga21Game) game).replaceScreen(new GameSettingScreen(game, assetStore, extendViewport, webSocketHandler, commonUtilInterface));
    }

    private void requestSignIn() {
        signInRequested = true;
        statusLabel.setText(AssetStore.SPLASH_TEXTS[3]);
        commonUtilInterface.showToast("Sign in with Google to play online");
        ((Piga21Game) game).getAuthInterface().signIn();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);

        // Check for internet connection
        if (!commonUtilInterface.isNetworkAvailable()) {
            commonUtilInterface.showToast("You are offline");
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // The first frame's delta includes loading the game, which would skip the splash entirely
        delta = Math.min(delta, 1 / 30f);

        elapsed += delta;
        boolean assetsReady = assetStore.update();

        // Fills with time, but never ahead of the real loading
        float progress = Math.min(Interpolation.smooth.apply(Math.min(1f, elapsed / SPLASH_SECONDS)), assetStore.progress());
        float filled = signInRequested ? Math.min(progress, 0.92f) : progress;
        barFill.setWidth(Math.max(BAR_HEIGHT, BAR_WIDTH * filled));
        if (assetsReady && !signInRequested) statusLabel.setText(AssetStore.SPLASH_TEXTS[2]);

        stage.act(delta);
        stage.draw();

        // Sign-in finishes in the background, so keep checking until we can move on
        if (!leaving && assetsReady && elapsed >= SPLASH_SECONDS) {
            continueToLobby();
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

}
