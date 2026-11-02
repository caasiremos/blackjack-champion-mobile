package com.piga21.screens;

import static com.piga21.utils.Util.startCountdown;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

import com.piga21.AssetStore;
import com.piga21.BackHandler;
import com.piga21.Piga21Game;
import com.piga21.points.GamePoint;
import com.piga21.sockets.WebSocketHandler;
import com.piga21.utils.CommonGameFunctions;
import com.piga21.utils.CommonUtilInterface;
import com.piga21.utils.OverlayContainer;
import com.piga21.utils.ChipBadge;
import com.piga21.utils.GameText;
import com.piga21.utils.PlayerApi;
import com.piga21.utils.ProfileDialog;
import com.piga21.utils.UiKit;
import com.piga21.utils.Util;

public class OnlinePlayersScreen implements Screen, BackHandler {

    private final Game game;
    private final AssetStore assetStore;
    private final WebSocketHandler webSocketHandler;
    private final CommonUtilInterface commonUtilInterface;
    private final Stage stage;
    private final ExtendViewport extendViewport;
    private String pendingRequestId; // store request ID when message arrives

    private final Skin skin;
    public Label timeCountLabel;
    boolean overlayShowing = false;

    private String outgoingRequestId;
    private String challengedName;

    private final Table playersContent = new Table();

    private final Map<String, Boolean> knownPlayers = new HashMap<>(); // Track known players
    private Timer.Task pollingTask;
    private final TextureRegion avatarTR, chipTR, dividerTR;
    private TextureRegion goldTR, silverTR, bronzeTR;
    private final Table leaderboardContent = new Table();
    private Label balanceLabel;


    public OnlinePlayersScreen(Game game, AssetStore assetStore, ExtendViewport extendViewport,
                               WebSocketHandler webSocketHandler,
                               CommonUtilInterface commonUtilInterface) {

        this.game = game;
        this.assetStore = assetStore;
        this.webSocketHandler = webSocketHandler;
        this.commonUtilInterface = commonUtilInterface;
        this.extendViewport = extendViewport;
        this.stage = new Stage(extendViewport);

        chipTR = assetStore.components.findRegion("chip_250");
        avatarTR = assetStore.otherComponents.findRegion("avatar");
        TextureRegion goldTR = assetStore.otherComponents.findRegion("gold");
        TextureRegion silverTR = assetStore.otherComponents.findRegion("silver");
        TextureRegion bronzeTR = assetStore.otherComponents.findRegion("bronze");
        TextureRegion inviteButtonTR = assetStore.otherComponents.findRegion("invite");
        dividerTR = assetStore.otherComponents.findRegion("divider");


        Image backgroundImage = assetStore.blueBackground();
        backgroundImage.setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        backgroundImage.setPosition(0, 0);

        float screenWidth = stage.getViewport().getWorldWidth();

        skin = new Skin();
        skin.add("default", new Label.LabelStyle(assetStore.listFont, Color.WHITE));

        playersContent.top();
        ScrollPane onlinePlayersScroll = new ScrollPane(playersContent);
        onlinePlayersScroll.setScrollingDisabled(true, false);
        onlinePlayersScroll.setFadeScrollBars(false);

        Table onlinePlayersWrapper = new Table();
        onlinePlayersWrapper.add(GameText.title(assetStore, "ONLINE GAMERS")).padBottom(20).row();
        onlinePlayersWrapper.add(onlinePlayersScroll).width(screenWidth * 0.4f).height(400).top().expand().fill();

        this.goldTR = goldTR;
        this.silverTR = silverTR;
        this.bronzeTR = bronzeTR;
        leaderboardContent.top();
        leaderboardContent.add(new Label("Loading...", skin)).pad(10);

        ScrollPane leaderboardScroll = new ScrollPane(leaderboardContent);
        leaderboardScroll.setScrollingDisabled(true, false);
        leaderboardScroll.setFadeScrollBars(false);

        Table leaderboardWrapper = new Table();
        leaderboardWrapper.top(); // line up with the online players column
        leaderboardWrapper.add(GameText.title(assetStore, "LEADERBOARD")).padBottom(20).row();
        leaderboardWrapper.add(leaderboardScroll).width(screenWidth * 0.4f).top();

        String selectedChip = ChipBadge.selectedKey();
        Table centerTable = new Table();
        centerTable.add(new ChipBadge(assetStore, selectedChip, 330)).row();
        balanceLabel = new Label(GamePoint.POINTS + new GamePoint().getPoints(), skin);
        centerTable.add(balanceLabel).padTop(20).row();

        Image inviteButtonImage = new Image(inviteButtonTR);
        // Invite friends who aren't playing yet (challenging someone online is a tap on their row)
        inviteButtonImage.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                commonUtilInterface.shareApp("Play Piga21 against me! Download the game and challenge me online.", "Piga21 challenge");
            }
        });
        Table bottomTable = new Table();
        bottomTable.add(inviteButtonImage).width(300).height(100).padTop(40).padBottom(20);
        bottomTable.setFillParent(true);
        bottomTable.bottom();

        Table mainLayout = new Table();
        mainLayout.setFillParent(true);
        mainLayout.top().padTop(110); // room for the back button

        mainLayout.add(onlinePlayersWrapper).width(screenWidth * 0.4f).top().padRight(20).expandY().fillY();
        mainLayout.add(centerTable).width(screenWidth * 0.2f).center().padRight(20).expandY().fillY();
        mainLayout.add(leaderboardWrapper).width(screenWidth * 0.4f).top().expandY().fillY();

        stage.addActor(backgroundImage);
        stage.addActor(mainLayout);
        stage.addActor(bottomTable);
        Util.addBackButton(stage, assetStore, (Piga21Game) game);
        addMyProfileButton();
    }

    private void listenToServer() {
        webSocketHandler.setMessageListener(message -> Gdx.app.postRunnable(() -> {
            Gdx.app.log("OnlinePlayersScreen", "Received: " + message);
            try {
                handleServerMessage(message, JsonParser.parseString(message).getAsJsonObject());
            } catch (Exception e) {
                Gdx.app.error("OnlinePlayersScreen", "Failed to handle message: " + message, e);
            }
        }));
    }

    private void handleServerMessage(String raw, JsonObject message) {
        if (message.has("status") && "error".equals(message.get("status").getAsString())) {
            commonUtilInterface.showToast(message.get("message").getAsString());
            return;
        }

        String action = message.has("action") ? message.get("action").getAsString() : "";
        PlayerApi.applyPlayerUpdate(message);
        balanceLabel.setText(GamePoint.POINTS + new GamePoint().getPoints());

        switch (action) {
            case "online_players" -> populatePlayersList(message.getAsJsonArray("players"), avatarTR, dividerTR, chipTR);
            case "player_profile" -> showPlayerProfile(message);
            case "leaderboard" -> populateLeaderboard(message.getAsJsonArray("players"),
                message.has("me") && message.get("me").isJsonObject() ? message.getAsJsonObject("me") : null);
            case "game_request" -> {
                pendingRequestId = message.get("request_id").getAsString();
                String fromName = message.has("from_name") ? message.get("from_name").getAsString() : "A player";
                int amount = message.has("amount") ? message.get("amount").getAsInt() : 0;
                boolean rematch = message.has("rematch") && message.get("rematch").getAsBoolean();
                acceptGameRequestDialog(rematch ? fromName + " wants a rematch!" : fromName + " challenges you!", "Stake: " + amount + " chips");
            }
            case "request_sent" -> {
                outgoingRequestId = message.get("request_id").getAsString();
                sendGameRequestDialog();
            }
            case "request_declined" -> closeRequestDialog("Your challenge was declined");
            case "request_expired" -> closeRequestDialog("The challenge expired");
            case "request_canceled" -> closeRequestDialog("The challenge was canceled");
            case "game_started" -> {
                closeRequestDialog(null);
                ((Piga21Game) game).pushScreen(new TwoPlayerGamePlayScreen(game, assetStore, extendViewport, webSocketHandler, commonUtilInterface, raw));
            }
            default -> { }
        }
    }

    /**
     * Server-ranked board: chips won against other players, ties broken by wins.
     * If you're not in the top list, your own rank is shown underneath.
     */
    private void populateLeaderboard(JsonArray players, JsonObject me) {
        leaderboardContent.clearChildren();
        int myId = Util.getUserId();
        boolean meListed = false;

        if (players.isEmpty()) {
            leaderboardContent.add(new Label("No games played yet", skin)).pad(10).row();
        }

        for (int i = 0; i < players.size(); i++) {
            JsonObject player = players.get(i).getAsJsonObject();
            boolean isMe = player.get("id").getAsInt() == myId;
            meListed |= isMe;

            addLeaderboardRow(player, isMe);
            if (i < players.size() - 1 && dividerTR != null) {
                leaderboardContent.add(new Image(dividerTR)).colspan(3).padTop(4).padBottom(4).width(800).height(5).row();
            }
        }

        if (!meListed && me != null && !me.get("rank").isJsonNull()) {
            if (dividerTR != null) {
                leaderboardContent.add(new Image(dividerTR)).colspan(3).padTop(4).padBottom(4).width(800).height(5).row();
            }
            addLeaderboardRow(me, true);
        }
    }

    private void addLeaderboardRow(JsonObject player, boolean isMe) {
        int rank = player.get("rank").getAsInt();
        Image icon = new Image(switch (rank) {
            case 1 -> goldTR;
            case 2 -> silverTR;
            case 3 -> bronzeTR;
            default -> chipTR;
        });

        String name = (rank > 3 ? "#" + rank + " " : "") + player.get("name").getAsString();
        Label nameLabel = new Label(name, skin);
        Label scoreLabel = new Label(String.valueOf(player.get("total_points").getAsInt()), skin);
        if (isMe) {
            nameLabel.setColor(Color.valueOf("#F4AD31"));
            scoreLabel.setColor(Color.valueOf("#F4AD31"));
        }

        Table row = new Table();
        row.add(icon).pad(10).size(80, 80);
        row.add(nameLabel).left().pad(10).width(180);
        row.add(scoreLabel).right().pad(10).width(80);
        int playerId = player.get("id").getAsInt();
        row.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (isMe) {
                    showMyProfile();
                } else {
                    requestPlayerProfile(playerId);
                }
            }
        });
        leaderboardContent.add(row).colspan(3).fillX().row();
    }

    private void addMyProfileButton() {
        Table button = new Table();
        button.add(new Image(avatarTR)).size(70, 70).padRight(16);
        button.add(GameText.label(assetStore.dialogFont, "My profile"));
        button.pack();
        button.setPosition(stage.getWidth() - button.getWidth() - 40, stage.getHeight() - button.getHeight() - 26);
        button.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showMyProfile();
            }
        });
        stage.addActor(button);
    }

    /**
     * Your own profile comes from the API (it includes your email, which other players never get).
     */
    private void showMyProfile() {
        PlayerApi.fetchProfile(new PlayerApi.Callback() {
            @Override
            public void onSuccess(JsonObject player) {
                ProfileDialog.show(stage, assetStore, commonUtilInterface, player, true, null, null);
            }

            @Override
            public void onFailure(String message) {
                commonUtilInterface.showToast("Couldn't load your profile: " + PlayerApi.readableMessage(message));
            }
        });
    }

    private void requestPlayerProfile(int playerId) {
        if (!webSocketHandler.isConnected()) {
            commonUtilInterface.showToast("Not connected to the game server");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("action", "player_profile");
        data.put("player_id", playerId);
        webSocketHandler.sendMessage(new Gson().toJson(data));
    }

    private void showPlayerProfile(JsonObject message) {
        JsonObject player = message.getAsJsonObject("player");
        boolean online = message.get("online").getAsBoolean();
        boolean inGame = message.get("in_game").getAsBoolean();
        int playerId = player.get("id").getAsInt();

        String status = inGame ? "In a game" : online ? "Online" : "Offline";
        // Only someone online and free can be challenged
        Runnable challenge = online && !inGame ? () -> initiateGameRequest(playerId, ProfileDialog.displayName(player)) : null;
        ProfileDialog.show(stage, assetStore, commonUtilInterface, player, false, status, challenge);
    }

    private void requestLeaderboard() {
        if (!webSocketHandler.isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", "leaderboard");
        webSocketHandler.sendMessage(new Gson().toJson(data));
    }

    private void closeRequestDialog(String toast) {
        pendingRequestId = null;
        outgoingRequestId = null;
        if (overlayShowing) {
            CommonGameFunctions.hideOverlayScreen(true);
            overlayShowing = false;
        }
        if (toast != null) {
            commonUtilInterface.showToast(toast);
        }
    }

    private void startPollingOnlinePlayers() {
        stopPollingOnlinePlayers();
        pollingTask = Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                getOnlinePlayers();
            }
        }, 0, 5); // Delay 0, repeat every 5 seconds
    }

    private void stopPollingOnlinePlayers() {
        if (pollingTask != null) {
            pollingTask.cancel();
        }
    }

    private void populatePlayersList(JsonArray playersArray, TextureRegion avatarTR, TextureRegion dividerTR, TextureRegion chipTR) {
        Gdx.app.log("PLAYERS", "populatePlayersList called with " + playersArray.size() + " players");
        playersContent.clearChildren();

        Map<String, Boolean> newKnownPlayers = new HashMap<>();
        int userId = Util.getUserId();

        Gdx.app.log("PLAYERS", "Current user ID: " + userId);

        for (int i = 0; i < playersArray.size(); i++) {
            JsonObject player = playersArray.get(i).getAsJsonObject();
            String name = player.get("name").getAsString();
            int balance = player.get("balance").getAsInt();
            int betAmount = player.get("bet_amount").getAsInt();
            int playerId = player.get("id").getAsInt();

            // Skip the current user
            if (playerId == userId) {
                Gdx.app.log("PLAYERS", "Skipping current user: " + name + " (ID: " + playerId + ")");
                continue;
            }

            Image avatar = new Image(avatarTR);
            String displayName = ProfileDialog.displayName(player);
            Label nameLabel = new Label(displayName, skin);
            nameLabel.setEllipsis(true);
            // Just the coin: its painted name would be unreadable at this size
            Image chip = new Image(ChipBadge.coinRegion(assetStore, ChipBadge.keyForAmount(betAmount)));

            // The whole card challenges; PROFILE (which doesn't pass its tap on) opens their profile
            Table row = new Table();
            row.setBackground(UiKit.card());
            row.setTouchable(Touchable.enabled);
            row.add(avatar).pad(8).size(80, 80);
            row.add(nameLabel).left().pad(8).width(360);
            row.add(chip).pad(8).size(96, 78);
            row.add(UiKit.button(assetStore, "PROFILE", UiKit.Style.SMALL, () -> requestPlayerProfile(playerId)))
                .height(70).padLeft(12);

            // Animate if this is a new player
            if (!knownPlayers.containsKey(name)) {
                row.getColor().a = 0f;
                row.addAction(Actions.fadeIn(0.5f));
            }

            row.addListener(new ClickListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    row.setBackground(UiKit.cardPressed());
                    return super.touchDown(event, x, y, pointer, button);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    row.setBackground(UiKit.card());
                    super.touchUp(event, x, y, pointer, button);
                }

                @Override
                public void clicked(InputEvent event, float x, float y) {
                    initiateGameRequest(playerId, displayName);
                }
            });

            playersContent.add(row).colspan(3).fillX().padBottom(14).row();
            Gdx.app.log("PLAYERS", "Added row for player: " + name);

            newKnownPlayers.put(name, true); // Save new player name
        }

        // The list can contain only us, which we skip, so check what was actually shown
        if (newKnownPlayers.isEmpty()) {
            Label empty = new Label("No one else is online right now.\nPlayers will appear here as they join.", skin);
            empty.setAlignment(com.badlogic.gdx.utils.Align.center);
            playersContent.add(empty).padTop(60).row();
        }

        knownPlayers.clear();
        knownPlayers.putAll(newKnownPlayers);

        Gdx.app.log("PLAYERS", "Final playersContent children: " + playersContent.getChildren().size);
        Gdx.app.log("PLAYERS", "Final playersContent size: " + playersContent.getWidth() + "x" + playersContent.getHeight());
    }

    private void acceptGameRequestDialog(String title, String detail) {
        timeCountLabel = GameText.label(assetStore.titleFont, "5");
        timeCountLabel.setColor(UiKit.GOLD);

        Table buttons = new Table();
        buttons.add(UiKit.button(assetStore, "DECLINE", UiKit.Style.SECONDARY, () -> {
            declineInitiatedGame();
            closeRequestDialog(null);
        })).width(300).height(100).padRight(40);
        buttons.add(UiKit.button(assetStore, "ACCEPT", UiKit.Style.PRIMARY, this::acceptInitiatedGame)).width(300).height(100);

        showRequestPanel(title, detail, buttons);

        // Not accepted in time counts as a decline
        final String requestId = pendingRequestId;
        startCountdown(timeCountLabel, () -> {
            if (requestId != null && requestId.equals(pendingRequestId)) {
                declineInitiatedGame();
                closeRequestDialog(null);
            }
        });

    }

    /**
     * Challenge dialogs share one look: title, detail line, countdown, buttons.
     */
    private void showRequestPanel(String title, String detail, Table buttons) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.8f);

        Table panel = new Table();
        panel.setBackground(UiKit.panel());
        panel.add(GameText.title(assetStore, title)).padBottom(10).row();
        Label detailLabel = GameText.label(assetStore.messageFont, detail);
        detailLabel.setColor(UiKit.MUTED);
        panel.add(detailLabel).padBottom(20).row();
        panel.add(timeCountLabel).padBottom(30).row();
        panel.add(buttons).row();
        panel.pack();
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() / 2 - panel.getHeight() / 2);

        overlay.addActor(panel);
        CommonGameFunctions.showOverlay();
        overlayShowing = true;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        // Also re-claims the socket when coming back from a game
        listenToServer();
        requestLeaderboard();

        // Check for internet connection
        if (!commonUtilInterface.isNetworkAvailable()) {
            commonUtilInterface.showToast("You are offline");
        }
        startPollingOnlinePlayers(); // Ensure polling restarts when screen is shown
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public boolean onBack() {
        // Back on a challenge dialog closes it (declining an incoming one) instead of leaving
        if (overlayShowing) {
            declineInitiatedGame();   // an incoming challenge
            cancelOutgoingRequest();  // or one we sent
            closeRequestDialog(null);
            return true;
        }
        return false;
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
        stopPollingOnlinePlayers(); // Stop polling when screen is hidden
    }

    @Override
    public void dispose() {
        stopPollingOnlinePlayers(); // Stop polling when screen is hidden
        stage.dispose();
    }

    private void sendGameRequestDialog() {
        timeCountLabel = GameText.label(assetStore.titleFont, "5");
        timeCountLabel.setColor(UiKit.GOLD);

        Table buttons = new Table();
        buttons.add(UiKit.button(assetStore, "CANCEL", UiKit.Style.SECONDARY, () -> {
            cancelOutgoingRequest();
            closeRequestDialog(null);
        })).width(300).height(100);

        String who = challengedName != null ? challengedName : "your opponent";
        showRequestPanel("CHALLENGE SENT", "Waiting for " + who + " to accept...", buttons);

        // No answer in time: withdraw the challenge (only if this dialog's challenge is still the open one)
        final String requestId = outgoingRequestId;
        startCountdown(timeCountLabel, () -> {
            if (requestId != null && requestId.equals(outgoingRequestId)) {
                cancelOutgoingRequest();
                closeRequestDialog(challengedName + " didn't answer");
            }
        });
    }

    private void initiateGameRequest(int opponentId, String opponentName) {
        challengedName = opponentName;
        Map<String, Object> data = new HashMap<>();
        data.put("action", "request_game");
        data.put("opponent_id", opponentId);

        // Use Gson instead of LibGDX Json
        String jsonString = new Gson().toJson(data);

        if (!webSocketHandler.isConnected()) {
            return;
        }

        webSocketHandler.sendMessage(jsonString);
    }

    private void acceptInitiatedGame() {
        if (pendingRequestId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", "accept_request");
        data.put("request_id", pendingRequestId);
        data.put("user_id", Util.getUserId());

        String jsonString = new Gson().toJson(data);
        Gdx.app.log("GameScreen", "Sending accept request: " + jsonString);

        if (webSocketHandler.isConnected()) {
            webSocketHandler.sendMessage(jsonString);
        }
    }

    private void cancelOutgoingRequest() {
        if (outgoingRequestId == null || !webSocketHandler.isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", "cancel_request");
        data.put("request_id", outgoingRequestId);
        webSocketHandler.sendMessage(new Gson().toJson(data));
        outgoingRequestId = null;
    }

    private void declineInitiatedGame() {
        if (pendingRequestId == null || !webSocketHandler.isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", "decline_request");
        data.put("request_id", pendingRequestId);
        webSocketHandler.sendMessage(new Gson().toJson(data));
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
