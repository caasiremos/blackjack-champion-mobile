package io.ngaigame.caasiremos.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;

import io.ngaigame.caasiremos.AssetStore;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import io.ngaigame.caasiremos.MainBlackJack;
import io.ngaigame.caasiremos.ads.AdHandler;
import io.ngaigame.caasiremos.auth.GoogleSignInInterface;
import io.ngaigame.caasiremos.sockets.WebSocketHandler;
import io.ngaigame.caasiremos.utils.GameText;
import io.ngaigame.caasiremos.utils.PlayerApi;
import io.ngaigame.caasiremos.utils.CommonUtilInterface;

/**
 * Shown at launch: the title art for a moment, then straight into the multiplayer lobby.
 */
public class SplashScreen implements Screen {
    private static final float SPLASH_SECONDS = 2.5f;

    Game game;
    ExtendViewport extendViewport;

    AssetStore assetStore;
    AdHandler adHandler;
    WebSocketHandler webSocketHandler;
    CommonUtilInterface commonUtilInterface;
    Stage stage;
    TextureRegion cardChipGroupTR, backgroundTR;
    Image backgroundImage, cardChipGroupImage;
    Label titleLabel;

    Table mainTable;

    private float elapsed = 0f;
    private boolean signInRequested = false;
    private boolean leaving = false;

    public SplashScreen(Game game, AssetStore assetStore, ExtendViewport extendViewport,
                        AdHandler adHandler, WebSocketHandler webSocketHandler,
                        CommonUtilInterface commonUtilInterface) {
        stage = new Stage(extendViewport);
        this.game = game;
        this.assetStore = assetStore;
        this.adHandler = adHandler;
        this.webSocketHandler = webSocketHandler;
        this.commonUtilInterface = commonUtilInterface;
        this.extendViewport = extendViewport;

        // Load textures
        cardChipGroupTR = assetStore.otherComponents.findRegion("card_chip_group");
        backgroundTR = assetStore.backgroundComponents.findRegion("background");

        // Create images
        backgroundImage = new Image(backgroundTR);
        backgroundImage.setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        backgroundImage.setPosition(0, 0);

        titleLabel = GameText.label(assetStore.heroFont, "BLACKJACK CHAMPION");
        cardChipGroupImage = new Image(cardChipGroupTR);

        // Create and setup main table
        mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.center();

        // The artwork takes whatever height is left, so the title always fits on screen
        cardChipGroupImage.setScaling(Scaling.fit);
        mainTable.add(cardChipGroupImage).padTop(40f).width(800).maxHeight(700).expandY().fillY().row();
        mainTable.add(titleLabel).padTop(10f).padBottom(80f);

        // Fade the title art in
        mainTable.getColor().a = 0f;
        mainTable.addAction(Actions.fadeIn(0.6f));

        // Add all actors to stage
        stage.addActor(backgroundImage);
        stage.addActor(mainTable);

        // If sign-in was dismissed, a tap on the splash brings the account picker back
        backgroundImage.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (signInRequested) requestSignIn();
            }
        });
    }

    /**
     * Online play needs a server account, so the lobby only opens once the player is signed in.
     */
    private void continueToLobby() {
        GoogleSignInInterface auth = ((MainBlackJack) game).getAuthInterface();
        if (auth != null && !auth.isSignedIn()) {
            if (!signInRequested) requestSignIn();
            return;
        }

        leaving = true;
        // Replace rather than push: back from the lobby exits instead of returning to the splash
        ((MainBlackJack) game).replaceScreen(new GameSettingScreen(game, assetStore, extendViewport, adHandler, webSocketHandler, commonUtilInterface));
    }

    private void requestSignIn() {
        signInRequested = true;
        commonUtilInterface.showToast("Sign in with Google to play online");
        ((MainBlackJack) game).getAuthInterface().signIn();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);

        // Check for internet connection
        if (!commonUtilInterface.isNetworkAvailable()) {
            commonUtilInterface.showToast("You are offline");
        }

        // Chips live on the server: send any rounds played offline, then refresh the local copy
        PlayerApi.syncBalance(null);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();

        // Sign-in finishes in the background, so keep checking until we can move on
        elapsed += delta;
        if (!leaving && elapsed >= SPLASH_SECONDS) {
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
