package com.piga21.screens;

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
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
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

import com.piga21.AssetStore;
import com.piga21.BackHandler;
import com.piga21.Piga21Game;
import com.piga21.utils.ChipBadge;
import com.piga21.utils.PlayerApi;
import com.piga21.points.GamePoint;
import com.piga21.sockets.WebSocketHandler;
import com.piga21.utils.CommonGameFunctions;
import com.piga21.utils.CommonUtilInterface;
import com.piga21.utils.OverlayContainer;
import com.piga21.utils.UiKit;
import com.piga21.utils.WalletDialog;
import com.piga21.utils.WalletHistoryDialog;
import com.piga21.wallet.WalletClient;
import com.piga21.wallet.WalletTransaction;
import com.piga21.utils.Util;

public class GameSettingScreen implements Screen, BackHandler {
    private final Game game;
    private final AssetStore assetStore;
    private final WebSocketHandler webSocketHandler;
    private final CommonUtilInterface commonUtilInterface;
    private final Stage stage;
    private final ExtendViewport extendViewport;

    private final Image streetChipImage, stakesChipImage, bossChipImage, legendChipImage, royalChipImage;
    private final Map<String, ChipBadge> chipBadges = new LinkedHashMap<>();
    private final Image backgroundImage;
    private String pendingRequestId; // store request ID when message arrives

    public Label timeCountLabel, opponentNameLabel;
    Label.LabelStyle  timeCountLabelStyle, opponentLabelStyle;
    boolean overlayShowing = false;
    private TextButton walletButton;
    private final WalletClient.Listener walletListener;

    public GameSettingScreen(Game game, AssetStore assetStore, ExtendViewport extendViewport,
                             WebSocketHandler webSocketHandler,
                             CommonUtilInterface commonUtilInterface) {
        this.game = game;
        this.extendViewport = extendViewport;
        this.assetStore = assetStore;
        this.webSocketHandler = webSocketHandler;
        this.commonUtilInterface = commonUtilInterface;
        this.stage = new Stage(extendViewport);

        // Load texture regions
        TextureRegion backgroundTR = assetStore.backgroundComponents.findRegion("background");

        // Create images
        // Coin art plus the chip's name in the game fonts; the coins are what you tap
        for (String key : ChipBadge.KEYS) {
            chipBadges.put(key, new ChipBadge(assetStore, key, 280));
        }
        streetChipImage = chipBadges.get("street").coin;
        stakesChipImage = chipBadges.get("stakes").coin;
        bossChipImage = chipBadges.get("boss").coin;
        legendChipImage = chipBadges.get("legend").coin;
        royalChipImage = chipBadges.get("royal").coin;
        backgroundImage = assetStore.blueBackground();

        // Set background full screen
        backgroundImage.setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        backgroundImage.setPosition(0, 0);

        // Chip layout
        Table chipTable = new Table();
        chipTable.center();
        for (ChipBadge badge : chipBadges.values()) {
            chipTable.add(badge).pad(10).padLeft(30).padRight(30);
        }

        // Button layout
        Table buttonTable = new Table();
        TextButton depositButton = UiKit.button(assetStore, "DEPOSIT", UiKit.Style.PRIMARY,
            () -> WalletDialog.show(stage, assetStore, true));
        TextButton withdrawButton = UiKit.button(assetStore, "WITHDRAW", UiKit.Style.SECONDARY,
            () -> WalletDialog.show(stage, assetStore, false));
        buttonTable.add(depositButton).expandX().left().padLeft(30).width(450).height(150);
        buttonTable.add().expandX(); // Spacer
        buttonTable.add(withdrawButton).expandX().right().padRight(30).width(450).height(150);

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
        Util.addBackButton(stage, assetStore, (Piga21Game) game);

        // Cash wallet (real money, not chips): balance top-right, opens the wallet and history
        WalletClient wallet = WalletClient.get();
        wallet.attach(webSocketHandler);
        walletButton = UiKit.button(assetStore, walletText(), UiKit.Style.SMALL, () -> WalletHistoryDialog.show(stage, assetStore));
        walletButton.pack();
        walletButton.setPosition(stage.getWidth() - walletButton.getWidth() - 40, stage.getHeight() - walletButton.getHeight() - 30);
        stage.addActor(walletButton);

        walletListener = new WalletClient.Listener() {
            @Override
            public void onWalletChanged() {
                walletButton.setText(walletText());
                walletButton.pack();
                walletButton.setX(stage.getWidth() - walletButton.getWidth() - 40);
            }

            @Override
            public void onTransactionUpdated(WalletTransaction transaction) {
                // Finished while the player wasn't watching it
                if (transaction.isFinished() && !WalletDialog.isShowing(transaction.reference)) {
                    String kind = transaction.isDeposit() ? "Deposit" : "Withdrawal";
                    commonUtilInterface.showToast(kind + " of " + WalletClient.get().money(transaction.amount) + " "
                        + transaction.statusText().toLowerCase());
                }
            }
        };
        wallet.addListener(walletListener);
    }

    private String walletText() {
        WalletClient wallet = WalletClient.get();
        return "WALLET  " + (wallet.isLoaded() ? wallet.money(wallet.getBalance()) : "UGX --");
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
                        // Wallet messages (including wallet errors) are handled and shown by the wallet UI
                        if (WalletClient.get().handle(jsonObject)) return;
                        PlayerApi.applyPlayerUpdate(jsonObject);

                        if ("game_request".equals(action)) {
                            pendingRequestId = jsonObject.get("request_id").getAsString();
                            String fromName = jsonObject.has("from_name") ? jsonObject.get("from_name").getAsString() : "A player";
                            showGameRequestDialog(fromName, jsonObject.get("amount").getAsInt());
                        } else if ("game_started".equals(action)) {
                            pendingRequestId = null;
                            CommonGameFunctions.hideOverlayScreen(true);
                            overlayShowing = false;
                            ((Piga21Game) game).pushScreen(new TwoPlayerGamePlayScreen(game, assetStore, extendViewport, webSocketHandler, commonUtilInterface, message));
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
        setupChipClick(streetChipImage, "street");
        setupChipClick(stakesChipImage, "stakes");
        setupChipClick(bossChipImage, "boss");
        setupChipClick(legendChipImage, "legend");
        setupChipClick(royalChipImage, "royal");
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
                setBetAmount(ChipBadge.amountOf(chipName));

                // Navigate to OnlinePlayersScreen
                ((Piga21Game) game).pushScreen(new OnlinePlayersScreen(
                    game, assetStore, extendViewport, webSocketHandler, commonUtilInterface
                ));
            }
        });
    }

    private void highlightSelectedChip() {
        String selectedChip = ChipBadge.selectedKey();

        for (Map.Entry<String, ChipBadge> badge : chipBadges.entrySet()) {
            badge.getValue().setSelected(badge.getKey().equals(selectedChip));
        }

        // Reset all chips
        Image[] allChips = {streetChipImage, stakesChipImage, bossChipImage, legendChipImage, royalChipImage};
        for (Image chip : allChips) {
            chip.clearActions();
            chip.setColor(Color.WHITE); // Reset tint
            chip.setScale(1f); // Reset scale
        }

        // Highlight the selected one
        Image selectedImage = switch (selectedChip) {
            case "stakes" -> stakesChipImage;
            case "boss" -> bossChipImage;
            case "legend" -> legendChipImage;
            case "royal" -> royalChipImage;
            default -> streetChipImage;
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
        // Pushes sent while another screen had the socket were missed, so ask for the real state
        WalletClient.get().refresh();

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
        WalletClient.get().removeListener(walletListener);
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

    private void showGameRequestDialog(String fromName, int amount) {
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
        // The server answers with game_started, which opens the table
        TextButton acceptButton = UiKit.button(assetStore, "ACCEPT", UiKit.Style.PRIMARY, this::acceptInitiatedGame);
        table.add(acceptButton).size(buttonWidth, buttonHeight).padBottom(20f).row();

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
