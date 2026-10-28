package io.ngaigame.caasiremos.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.ngaigame.caasiremos.AssetStore;
import io.ngaigame.caasiremos.BackHandler;
import io.ngaigame.caasiremos.MainBlackJack;
import io.ngaigame.caasiremos.utils.ChipBadge;
import io.ngaigame.caasiremos.utils.PlayerApi;
import io.ngaigame.caasiremos.ads.AdHandler;
import io.ngaigame.caasiremos.points.GamePoint;
import io.ngaigame.caasiremos.sockets.WebSocketHandler;
import io.ngaigame.caasiremos.utils.CommonGameFunctions;
import io.ngaigame.caasiremos.utils.CommonUtilInterface;
import io.ngaigame.caasiremos.utils.OverlayContainer;
import io.ngaigame.caasiremos.utils.Util;

public class GameSettingScreen implements Screen, BackHandler {
    private final Game game;
    private final AssetStore assetStore;
    private final AdHandler adHandler;
    private final WebSocketHandler webSocketHandler;
    private final CommonUtilInterface commonUtilInterface;
    private final Stage stage;
    private final ExtendViewport extendViewport;

    private final Image rockieChipImage, streetChipImage, casinoChipImage, stakesChipImage, bossChipImage;
    private final Map<String, ChipBadge> chipBadges = new LinkedHashMap<>();
    private final Image helpButtonImage, settingButtonImage, backgroundImage;
    private String pendingRequestId; // store request ID when message arrives

    public Label timeCountLabel, opponentNameLabel;
    Label.LabelStyle  timeCountLabelStyle, opponentLabelStyle;
    boolean overlayShowing = false;

    public GameSettingScreen(Game game, AssetStore assetStore, ExtendViewport extendViewport,
                             AdHandler adHandler, WebSocketHandler webSocketHandler,
                             CommonUtilInterface commonUtilInterface) {
        this.game = game;
        this.extendViewport = extendViewport;
        this.assetStore = assetStore;
        this.adHandler = adHandler;
        this.webSocketHandler = webSocketHandler;
        this.commonUtilInterface = commonUtilInterface;
        this.stage = new Stage(extendViewport);

        // Load texture regions
        TextureRegion helpButtonTR = assetStore.otherComponents.findRegion("button_help");
        TextureRegion settingButtonTR = assetStore.otherComponents.findRegion("button_settings");
        TextureRegion backgroundTR = assetStore.backgroundComponents.findRegion("background");

        // Create images
        // Coin art plus the chip's name in the game fonts; the coins are what you tap
        for (String key : new String[]{"rockie", "street", "casino", "stakes", "boss"}) {
            chipBadges.put(key, new ChipBadge(assetStore, key, 240));
        }
        rockieChipImage = chipBadges.get("rockie").coin;
        streetChipImage = chipBadges.get("street").coin;
        casinoChipImage = chipBadges.get("casino").coin;
        stakesChipImage = chipBadges.get("stakes").coin;
        bossChipImage = chipBadges.get("boss").coin;
        helpButtonImage = new Image(helpButtonTR);
        settingButtonImage = new Image(settingButtonTR);
        backgroundImage = assetStore.blueBackground();

        // Set background full screen
        backgroundImage.setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        backgroundImage.setPosition(0, 0);

        // Responsive sizes
        float screenWidth = stage.getViewport().getWorldWidth();
        float screenHeight = stage.getViewport().getWorldHeight();
        float buttonWidth = screenWidth * 10;
        float buttonHeight = screenHeight * 5f;

        helpButtonImage.setSize(buttonWidth, buttonHeight);
        settingButtonImage.setSize(buttonWidth, buttonHeight);

        helpButtonImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                helpButtonImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                helpButtonImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
            }
        });

        settingButtonImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                settingButtonImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                settingButtonImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
            }
        });


        // Chip layout
        Table chipTable = new Table();
        chipTable.center();
        for (ChipBadge badge : chipBadges.values()) {
            chipTable.add(badge).pad(10).padLeft(30).padRight(30);
        }

        // Button layout
        Table buttonTable = new Table();
        buttonTable.add(helpButtonImage).expandX().left().padLeft(30).width(450).height(150);
        buttonTable.add().expandX(); // Spacer
        buttonTable.add(settingButtonImage).expandX().right().padRight(30).width(450).height(150);

        // Main layout
        Table mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.top().padTop(100);
//        mainTable.add(chipTable).expand().center().padTop(350).row();
        mainTable.add(chipTable).expand().center().row();
        mainTable.add(buttonTable).expandX().bottom().padBottom(40).fillX();

        // Add actors
        stage.addActor(backgroundImage);
        stage.addActor(mainTable);

        authenticateUser();


        handleChipClicks();
        highlightSelectedChip();
        getOnlinePlayers();
        Util.addBackButton(stage, assetStore, (MainBlackJack) game);
    }

    private void listenToServer() {
        webSocketHandler.setMessageListener(new WebSocketHandler.MessageListener() {
            @Override
            public void onMessage(String message) {
                Gdx.app.postRunnable(() -> {
                    Gdx.app.log("GameScreen", "Received: " + message);

                    try {
                        JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
                        String action = jsonObject.has("action") ? jsonObject.get("action").getAsString() : "";
                        PlayerApi.applyPlayerUpdate(jsonObject);

                        if ("game_request".equals(action)) {
                            pendingRequestId = jsonObject.get("request_id").getAsString();
                            String fromName = jsonObject.has("from_name") ? jsonObject.get("from_name").getAsString() : "A player";
                            showGameRequestDialog(settingButtonImage, fromName, jsonObject.get("amount").getAsInt());
                        } else if ("game_started".equals(action)) {
                            pendingRequestId = null;
                            CommonGameFunctions.hideOverlayScreen(true);
                            overlayShowing = false;
                            ((MainBlackJack) game).pushScreen(new TwoPlayerGamePlayScreen(game, assetStore, extendViewport, adHandler, webSocketHandler, commonUtilInterface, message));
                        } else if ("request_canceled".equals(action) || "request_expired".equals(action)) {
                            pendingRequestId = null;
                            CommonGameFunctions.hideOverlayScreen(true);
                            overlayShowing = false;
                        } else if (jsonObject.has("status") && "error".equals(jsonObject.get("status").getAsString())) {
                            commonUtilInterface.showToast(jsonObject.get("message").getAsString());
                        }
                    } catch (Exception e) {
                        Gdx.app.error("GameScreen", "Failed to parse message or missing request_id", e);
                    }
                });
            }
        });
    }

    private void handleChipClicks() {
        setupChipClick(rockieChipImage, "rockie");
        setupChipClick(streetChipImage, "street");
        setupChipClick(casinoChipImage, "casino");
        setupChipClick(stakesChipImage, "stakes");
        setupChipClick(bossChipImage, "boss");
    }

    private void setupChipClick(Image chipImage, String chipName) {
        chipImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                chipImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                chipImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));

                // Save selected chip to preferences
                Preferences prefs = Gdx.app.getPreferences(GamePoint.GAME_PREFS);
                prefs.putString("selected_chip", chipName);
                prefs.flush();

                highlightSelectedChip();

                // Set bet amount based on selected chip
                switch (chipName) {
                    case "rockie" -> setBetAmount(50);
                    case "street" -> setBetAmount(100);
                    case "casino" -> setBetAmount(250);
                    case "stakes" -> setBetAmount(500);
                    case "boss" -> setBetAmount(1000);
                }

                // Navigate to OnlinePlayersScreen
                ((MainBlackJack) game).pushScreen(new OnlinePlayersScreen(
                    game, assetStore, extendViewport, adHandler, webSocketHandler, commonUtilInterface
                ));
            }
        });
    }

    private void highlightSelectedChip() {
        Preferences prefs = Gdx.app.getPreferences(GamePoint.GAME_PREFS);
        String selectedChip = prefs.getString("selected_chip", "rockie");

        for (Map.Entry<String, ChipBadge> badge : chipBadges.entrySet()) {
            badge.getValue().setSelected(badge.getKey().equals(selectedChip));
        }

        // Reset all chips
        Image[] allChips = {rockieChipImage, streetChipImage, casinoChipImage, stakesChipImage, bossChipImage};
        for (Image chip : allChips) {
            chip.clearActions();
            chip.setColor(Color.WHITE); // Reset tint
            chip.setScale(1f); // Reset scale
        }

        // Highlight the selected one
        Image selectedImage = switch (selectedChip) {
            case "rockie" -> rockieChipImage;
            case "street" -> streetChipImage;
            case "casino" -> casinoChipImage;
            case "stakes" -> stakesChipImage;
            case "boss" -> bossChipImage;
            default -> rockieChipImage;
        };

        selectedImage.setColor(Color.GOLD);
        selectedImage.addAction(Actions.forever(Actions.sequence(
            Actions.scaleTo(1.1f, 1.1f, 0.5f),
            Actions.scaleTo(1f, 1f, 0.5f)
        )));
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        // Also re-claims the socket when coming back from the lobby or a game
        listenToServer();

        // Check for internet connection
        if (!commonUtilInterface.isNetworkAvailable()) {
            commonUtilInterface.showToast("You are offline");
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public boolean onBack() {
        // Back on a challenge dialog declines it instead of leaving the screen
        if (overlayShowing) {
            declineInitiatedGame();
            pendingRequestId = null;
            CommonGameFunctions.hideOverlayScreen(true);
            overlayShowing = false;
            return true;
        }
        return false;
    }

    private void declineInitiatedGame() {
        if (pendingRequestId == null || !webSocketHandler.isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", "decline_request");
        data.put("request_id", pendingRequestId);
        webSocketHandler.sendMessage(new Gson().toJson(data));
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

    private void authenticateUser() {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "authenticate");
        data.put("user_id", Util.getUserId());
        data.put("token", Util.getApiToken());

        // Use Gson instead of LibGDX Json
        String jsonString = new Gson().toJson(data);

        if (!webSocketHandler.isConnected()) {
            return;
        }

        webSocketHandler.sendMessage(jsonString);
    }

    private void showGameRequestDialog(Image image, String fromName, int amount) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage);

        float buttonWidth = 370f, buttonHeight = 120f;

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        opponentLabelStyle = new Label.LabelStyle(assetStore.dialogFont, Color.valueOf("#EB2F23"));
        timeCountLabelStyle = new Label.LabelStyle(assetStore.dialogFont, Color.valueOf("#EB2F23"));

        opponentNameLabel = new Label(fromName + " challenges you for " + amount + ". Accept?", opponentLabelStyle);
        timeCountLabel = new Label("5", timeCountLabelStyle);

        // Arrange items vertically
        table.add(opponentNameLabel).padBottom(20f).row();
        table.add(timeCountLabel).padBottom(20f).row();
        table.add(image).size(buttonWidth, buttonHeight).padBottom(20f).row();

        overlay.addActor(table);
        CommonGameFunctions.showOverlay();
        overlayShowing = true;

        // Start countdown timer
        final int[] countdown = {5}; // Start from 5 seconds
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        if (countdown[0] > 0) {
                            timeCountLabel.setText(String.valueOf(countdown[0]));
                            countdown[0]--;
                        } else {
                            // Time's up - hide overlay and cancel request
                            CommonGameFunctions.hideOverlayScreen(true);
                            overlayShowing = false;
                        }
                    }
                });
            }
        }, 0, 1, 5);

        // The same button is reused for every request
        image.clearListeners();
        image.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                image.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                image.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                // The server answers with game_started, which opens the table
                acceptInitiatedGame();
            }
        });

    }


    private void acceptInitiatedGame() {
        if (pendingRequestId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", "accept_request");
        data.put("request_id", pendingRequestId);
        data.put("user_id", Util.getUserId());

        String jsonString = new Gson().toJson(data);

        if (webSocketHandler.isConnected()) {
            webSocketHandler.sendMessage(jsonString);
        }
    }


    private void setBetAmount(int betAmount) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "set_bet_amount");
        data.put("bet_amount", betAmount);
        String jsonString = new Gson().toJson(data);
        if (webSocketHandler.isConnected()) {
            webSocketHandler.sendMessage(jsonString);
        }
    }

    private void getOnlinePlayers() {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "list_players");
        String jsonString = new Gson().toJson(data);
        if (webSocketHandler.isConnected()) {
            webSocketHandler.sendMessage(jsonString);
        }
    }
}
