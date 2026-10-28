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

public class GameModeScreen implements Screen {
    Game game;
    ExtendViewport extendViewport;

    AssetStore assetStore;
    AdHandler adHandler;
    WebSocketHandler webSocketHandler;
    CommonUtilInterface commonUtilInterface;
    Stage stage;
    TextureRegion cardChipGroupTR, classicButtonTR, twoPlayerTR, backgroundTR;
    Image backgroundImage, cardChipGroupImage, classicButtonImage, twoPlayerImage;
    Label titleLabel;


    Table mainTable;

    public GameModeScreen(Game game, AssetStore assetStore, ExtendViewport extendViewport,
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
        classicButtonTR = assetStore.otherComponents.findRegion("button_classic");
        twoPlayerTR = assetStore.otherComponents.findRegion("button_two_player");
        backgroundTR = assetStore.backgroundComponents.findRegion("background");

        // Create images
        backgroundImage = new Image(backgroundTR);
        backgroundImage.setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        backgroundImage.setPosition(0, 0);

        titleLabel = GameText.label(assetStore.heroFont, "BLACKJACK");
        classicButtonImage = new Image(classicButtonTR);
        twoPlayerImage = new Image(twoPlayerTR);
        cardChipGroupImage = new Image(cardChipGroupTR);

        classicButtonImage.setSize(1000, 500);

        // Create and setup main table
        mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.top(); // Align content to top

        // Create a table for the side-by-side buttons
        Table buttonsTable = new Table();
        buttonsTable.add(classicButtonImage).width(380).height(130).padRight(40f).padBottom(30f);
        buttonsTable.add(twoPlayerImage).width(380).height(130).padBottom(30f);

        // Build the main layout
        // The artwork takes whatever height is left, so the title and buttons always fit on screen
        cardChipGroupImage.setScaling(Scaling.fit);
        mainTable.add(cardChipGroupImage).padTop(40f).width(800).maxHeight(700).expandY().fillY().row();
        mainTable.add(titleLabel).padTop(10f).row();
        mainTable.add(buttonsTable).padTop(30f).padBottom(20f);

        // Add all actors to stage
        stage.addActor(backgroundImage);
        stage.addActor(mainTable);

        twoPlayerImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                twoPlayerImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                twoPlayerImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));

                // Online play needs a server account; start signing in rather than opening a dead lobby
                GoogleSignInInterface auth = ((MainBlackJack) game).getAuthInterface();
                if (auth != null && !auth.isSignedIn()) {
                    commonUtilInterface.showToast("Sign in with Google to play online");
                    auth.signIn();
                    return;
                }

                ((MainBlackJack) game).pushScreen(new GameSettingScreen(game, assetStore, extendViewport, adHandler, webSocketHandler, commonUtilInterface));
            }
        });

        classicButtonImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                classicButtonImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                classicButtonImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                ((MainBlackJack) game).pushScreen(new ClassicGamePlayScreen(game, assetStore, extendViewport, adHandler, webSocketHandler, commonUtilInterface));
            }
        });
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
