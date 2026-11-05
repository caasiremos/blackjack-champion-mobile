package com.piga21.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.piga21.AssetStore;
import com.piga21.BackHandler;
import com.piga21.Piga21Game;
import com.piga21.utils.ChipBadge;
import com.piga21.utils.GameText;
import com.piga21.utils.PlayerApi;
import com.piga21.points.GamePoint;
import com.piga21.sockets.WebSocketHandler;
import com.piga21.utils.CommonGameFunctions;
import com.piga21.utils.CommonUtilInterface;
import com.piga21.utils.OverlayContainer;
import com.piga21.utils.ProfileDialog;
import com.piga21.utils.SettingsDialog;
import com.piga21.utils.UiKit;
import com.piga21.utils.WalletDialog;
import com.piga21.wallet.WalletClient;
import com.piga21.wallet.WalletTransaction;
import com.piga21.utils.Util;

/**
 * The main menu: the player's profile and settings along the top, the stake chips in the
 * middle (picking one opens the lobby), and the points balance with top up / redeem below.
 */
public class GameSettingScreen implements Screen, BackHandler {
    private static final float COIN_WIDTH = 270;
    private static final float TOP_BAR_HEIGHT = 136;

    private final Game game;
    private final AssetStore assetStore;
    private final WebSocketHandler webSocketHandler;
    private final CommonUtilInterface commonUtilInterface;
    private final Stage stage;
    private final ExtendViewport extendViewport;

    private final Map<String, ChipBadge> chipBadges = new LinkedHashMap<>();
    private final Map<String, Table> stakeCards = new LinkedHashMap<>();
    private final Image backgroundImage;
    private String pendingRequestId; // store request ID when message arrives

    public Label timeCountLabel;
    boolean overlayShowing = false;

    private Label initialLabel, nameLabel, detailLabel, pointsLabel;
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

        backgroundImage = assetStore.redBackground();
        backgroundImage.setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        backgroundImage.setPosition(0, 0);

        Table root = new Table();
        root.setFillParent(true);
        root.top();
        root.add(buildTopBar()).expandX().fillX().height(TOP_BAR_HEIGHT).row();
        root.add(new Image(UiKit.rule())).expandX().fillX().height(3).row();
        root.add(buildHeading()).padTop(44).row();
        root.add(buildStakes()).expand().center().row();
        root.add(buildBottomBar()).expandX().fillX().padLeft(48).padRight(48).padBottom(40);

        stage.addActor(backgroundImage);
        stage.addActor(root);

        authenticateUser();
        highlightSelectedChip();
        getOnlinePlayers();
        showPlayer(null);
        playEntrance();

        WalletClient wallet = WalletClient.get();
        wallet.attach(webSocketHandler);
        walletListener = new WalletClient.Listener() {
            @Override
            public void onWalletChanged() {
                pointsLabel.setText(pointsText());
            }

            @Override
            public void onTransactionUpdated(WalletTransaction transaction) {
                // Finished while the player wasn't watching it
                if (transaction.isFinished() && !WalletDialog.isShowing(transaction.reference)) {
                    String kind = transaction.isDeposit() ? "Top-up" : "Redemption";
                    commonUtilInterface.showToast(kind + " of " + WalletClient.get().money(transaction.amount) + " "
                        + transaction.statusText().toLowerCase());
                }
            }
        };
        wallet.addListener(walletListener);
    }

    // ---- Layout ----

    /**
     * Profile on the left, the game's name in the middle, settings on the right.
     */
    private Table buildTopBar() {
        Table bar = new Table();
        bar.setBackground(UiKit.bar());
        bar.pad(0, 44, 0, 44);

        // Profile: the player's initial in a gold badge, their name, and their rank underneath
        Stack badge = new Stack();
        badge.add(new Image(UiKit.circle("avatarBadge", UiKit.GOLD, UiKit.GOLD_DARK, 46, 5)));
        initialLabel = GameText.label(assetStore.buttonFont, "?");
        initialLabel.setColor(UiKit.BUTTON_TEXT);
        initialLabel.setAlignment(Align.center);
        badge.add(initialLabel);

        nameLabel = GameText.label(assetStore.buttonFont, "Player");
        detailLabel = GameText.label(assetStore.chipTaglineFont, "");
        detailLabel.setColor(UiKit.MUTED);
        Table names = new Table();
        names.add(nameLabel).left().row();
        names.add(detailLabel).left().padTop(2);

        Table profile = new Table();
        profile.add(badge).size(92, 92).padRight(22);
        profile.add(names).left();
        tappable(profile, this::showMyProfile);

        Label brand = GameText.label(assetStore.titleFont, "PIGA21");
        brand.setColor(UiKit.GOLD);

        TextureRegion settingsArt = assetStore.otherComponents.findRegion("button_settings");
        // The art is a round gear icon followed by a "SETTINGS" pill; only the icon is used
        Image settings = new Image(new TextureRegion(settingsArt, 0, 0, settingsArt.getRegionHeight(), settingsArt.getRegionHeight()));
        settings.setOrigin(Align.center);
        tappable(settings, () -> SettingsDialog.show(stage, assetStore, commonUtilInterface, this::showMyProfile));

        bar.add(profile).left().expandX().uniformX();
        bar.add(brand).center();
        Table right = new Table();
        right.add(settings).size(96, 96);
        bar.add(right).right().expandX().uniformX();
        return bar;
    }

    private Table buildHeading() {
        Table heading = new Table();
        heading.add(GameText.title(assetStore, "CHOOSE YOUR STAKE")).row();
        Label hint = GameText.label(assetStore.messageFont, "Pick what you play for, then challenge a player");
        hint.setColor(UiKit.MUTED);
        heading.add(hint).padTop(6);
        return heading;
    }

    /**
     * One card per chip; tapping a card picks that stake and opens the lobby.
     */
    private Table buildStakes() {
        Table row = new Table();
        for (String key : ChipBadge.KEYS) {
            ChipBadge badge = new ChipBadge(assetStore, key, COIN_WIDTH);
            chipBadges.put(key, badge);

            Table card = new Table();
            card.setBackground(UiKit.stakeCard(false));
            card.add(badge);
            stakeCards.put(key, card);
            tappable(card, () -> pickStake(key));

            row.add(card).pad(0, 18, 0, 18);
        }
        return row;
    }

    /**
     * Top up on the left, the points balance in the middle, redeem on the right.
     */
    private Table buildBottomBar() {
        TextButton topUp = UiKit.button(assetStore, "TOP UP", UiKit.Style.PRIMARY,
            () -> WalletDialog.show(stage, assetStore, true));
        TextButton redeem = UiKit.button(assetStore, "REDEEM", UiKit.Style.SECONDARY,
            () -> WalletDialog.show(stage, assetStore, false));

        Table points = new Table();
        points.setBackground(UiKit.pill());
        Label caption = GameText.label(assetStore.chipTaglineFont, "POINTS");
        caption.setColor(UiKit.MUTED);
        pointsLabel = GameText.label(assetStore.buttonFont, pointsText());
        pointsLabel.setColor(UiKit.GOLD);
        points.add(caption).row();
        points.add(pointsLabel).padTop(2);

        Table bar = new Table();
        bar.add(topUp).width(400).height(130).left().expandX().uniformX();
        bar.add(points).minWidth(420).height(120);
        Table right = new Table();
        right.add(redeem).width(400).height(130);
        bar.add(right).right().expandX().uniformX();
        return bar;
    }

    /**
     * Fades the stake cards in one after another.
     */
    private void playEntrance() {
        int i = 0;
        for (Table card : stakeCards.values()) {
            card.getColor().a = 0;
            card.addAction(Actions.sequence(Actions.delay(0.08f * i++), Actions.fadeIn(0.35f)));
        }
    }

    /**
     * Grows a little while pressed, like the rest of the game's buttons.
     */
    private void tappable(Actor actor, Runnable onTap) {
        actor.setTouchable(Touchable.enabled);
        if (actor instanceof Group group) group.setTransform(true);
        actor.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                actor.setOrigin(Align.center);
                actor.addAction(Actions.scaleTo(1.05f, 1.05f, 0.08f));
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                actor.addAction(Actions.scaleTo(1f, 1f, 0.08f));
                super.touchUp(event, x, y, pointer, button);
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                onTap.run();
            }
        });
    }

    // ---- Player ----

    private String pointsText() {
        WalletClient wallet = WalletClient.get();
        return wallet.isLoaded() ? wallet.money(wallet.getBalance()) : "-- points";
    }

    /**
     * Fills the profile in the top bar; null leaves it as it is until the server sends the player.
     */
    private void showPlayer(JsonObject player) {
        if (player == null) {
            return;
        }

        String name = ProfileDialog.displayName(player);
        nameLabel.setText(name);
        initialLabel.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.US));

        String rank = player.has("rank") && !player.get("rank").isJsonNull() ? "Rank #" + player.get("rank").getAsInt() + " this week" : "No wins this week";
        detailLabel.setText(rank);
    }

    private void showMyProfile() {
        PlayerApi.fetchProfile(new PlayerApi.Callback() {
            @Override
            public void onSuccess(JsonObject player) {
                showPlayer(player);
                ProfileDialog.show(stage, assetStore, commonUtilInterface, player, true, null, null, GameSettingScreen.this::showPlayer);
            }

            @Override
            public void onFailure(String message) {
                commonUtilInterface.showToast("Couldn't load your profile: " + PlayerApi.readableMessage(message));
            }
        });
    }

    private void refreshPlayer() {
        PlayerApi.fetchProfile(new PlayerApi.Callback() {
            @Override
            public void onSuccess(JsonObject player) {
                showPlayer(player);
            }

            @Override
            public void onFailure(String message) {
                showPlayer(null);
            }
        });
    }

    // ---- Stakes ----

    private void pickStake(String chipName) {
        // A stake is paid in points with its service fee on top, so you need both
        int amount = ChipBadge.amountOf(chipName);
        WalletClient wallet = WalletClient.get();
        int needed = amount + ChipBadge.feeFor(amount);
        if (wallet.isLoaded() && wallet.getBalance() < needed) {
            commonUtilInterface.showToast(String.format(Locale.US, "You need %,d points to stake %,d. Top up to play.", needed, amount));
            // Straight to topping up, so they can play this stake
            WalletDialog.show(stage, assetStore, true);
            return;
        }

        Preferences prefs = Gdx.app.getPreferences(GamePoint.GAME_PREFS);
        prefs.putString("selected_chip", chipName);
        prefs.flush();

        highlightSelectedChip();
        setBetAmount(amount);

        ((Piga21Game) game).pushScreen(new OnlinePlayersScreen(
            game, assetStore, extendViewport, webSocketHandler, commonUtilInterface
        ));
    }

    private void highlightSelectedChip() {
        String selectedChip = ChipBadge.selectedKey();

        for (Map.Entry<String, ChipBadge> entry : chipBadges.entrySet()) {
            boolean selected = entry.getKey().equals(selectedChip);
            ChipBadge badge = entry.getValue();
            badge.setSelected(selected);
            stakeCards.get(entry.getKey()).setBackground(UiKit.stakeCard(selected));

            badge.coin.clearActions();
            badge.coin.setScale(1f);
            if (selected) {
                // A slow breathe on the picked chip
                badge.coin.addAction(Actions.forever(Actions.sequence(
                    Actions.scaleTo(1.05f, 1.05f, 0.9f),
                    Actions.scaleTo(1f, 1f, 0.9f)
                )));
            }
        }
    }

    // ---- Server ----

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
                        boolean aboutMe = "authenticated".equals(action) || "balance_updated".equals(action) || "profile".equals(action);
                        if (aboutMe && jsonObject.has("player") && jsonObject.get("player").isJsonObject()) {
                            showPlayer(jsonObject.getAsJsonObject("player"));
                        }

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

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        // Also re-claims the socket when coming back from the lobby or a game
        listenToServer();
        // Pushes sent while another screen had the socket were missed, so ask for the real state
        WalletClient.get().refresh();
        pointsLabel.setText(pointsText());
        highlightSelectedChip();
        refreshPlayer();

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
            declineChallenge();
            return true;
        }
        return false;
    }

    private void declineChallenge() {
        declineInitiatedGame();
        pendingRequestId = null;
        CommonGameFunctions.hideOverlayScreen(true);
        overlayShowing = false;
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
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.8f);

        Table panel = new Table();
        panel.setBackground(UiKit.panel());
        panel.add(GameText.title(assetStore, "CHALLENGE!")).colspan(2).row();

        Label message = GameText.label(assetStore.messageFont,
            fromName + " wants to play you for " + String.format(Locale.US, "%,d points (+%,d service points)", amount, ChipBadge.feeFor(amount)));
        panel.add(message).colspan(2).padTop(14).row();

        timeCountLabel = GameText.label(assetStore.heroFont, String.valueOf(Util.CHALLENGE_SECONDS));
        timeCountLabel.setColor(UiKit.GOLD);
        panel.add(timeCountLabel).colspan(2).padTop(10).padBottom(10).row();

        // The server answers with game_started, which opens the table
        panel.add(UiKit.button(assetStore, "DECLINE", UiKit.Style.SECONDARY, this::declineChallenge))
            .width(320).height(110).padRight(24);
        panel.add(UiKit.button(assetStore, "ACCEPT", UiKit.Style.PRIMARY, this::acceptInitiatedGame))
            .width(320).height(110);

        panel.pack();
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() / 2 - panel.getHeight() / 2);
        overlay.addActor(panel);
        CommonGameFunctions.showOverlay();
        overlayShowing = true;

        // Start countdown timer
        final int[] countdown = {Util.CHALLENGE_SECONDS};
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
        }, 0, 1, Util.CHALLENGE_SECONDS);
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
