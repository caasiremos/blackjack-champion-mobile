package com.piga21.screens;

import static com.piga21.utils.Util.startCountdown;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.piga21.AssetStore;
import com.piga21.BackHandler;
import com.piga21.Piga21Game;
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
import com.piga21.wallet.WalletClient;

/**
 * The lobby: who's online to challenge (left), your stake and chips (middle) and the
 * leaderboard (right), under a top bar with Back and Share.
 */
public class OnlinePlayersScreen implements Screen, BackHandler {
    private static final float TOP_BAR_HEIGHT = 136;

    private final Game game;
    private final AssetStore assetStore;
    private final WebSocketHandler webSocketHandler;
    private final CommonUtilInterface commonUtilInterface;
    private final Stage stage;
    private final ExtendViewport extendViewport;
    private String pendingRequestId; // store request ID when message arrives

    public Label timeCountLabel;
    boolean overlayShowing = false;

    private String outgoingRequestId;
    private String challengedName;

    private final Table playersContent = new Table();
    private final Table leaderboardContent = new Table();
    private final Map<String, Boolean> knownPlayers = new HashMap<>(); // Track known players
    private Timer.Task pollingTask;
    private final TextureRegion goldTR, silverTR, bronzeTR;
    private Label balanceLabel, onlineCountLabel;


    public OnlinePlayersScreen(Game game, AssetStore assetStore, ExtendViewport extendViewport,
                               WebSocketHandler webSocketHandler,
                               CommonUtilInterface commonUtilInterface) {

        this.game = game;
        this.assetStore = assetStore;
        this.webSocketHandler = webSocketHandler;
        this.commonUtilInterface = commonUtilInterface;
        this.extendViewport = extendViewport;
        this.stage = new Stage(extendViewport);

        goldTR = assetStore.otherComponents.findRegion("gold");
        silverTR = assetStore.otherComponents.findRegion("silver");
        bronzeTR = assetStore.otherComponents.findRegion("bronze");

        Image backgroundImage = assetStore.redBackground();
        backgroundImage.setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        backgroundImage.setPosition(0, 0);

        float width = stage.getViewport().getWorldWidth();
        float sideWidth = width * 0.37f;

        // Online players
        onlineCountLabel = GameText.label(assetStore.chipTaglineFont, "");
        onlineCountLabel.setColor(UiKit.MUTED);
        playersContent.top();
        playersContent.add(muted("Looking for players...")).padTop(60);
        Table players = section("ONLINE NOW", onlineCountLabel, playersContent);

        // Leaderboard
        Label leaderboardHint = GameText.label(assetStore.chipTaglineFont, "Wins this week");
        leaderboardHint.setColor(UiKit.MUTED);
        leaderboardContent.top();
        leaderboardContent.add(muted("Loading...")).padTop(60);
        Table leaderboard = section("LEADERBOARD", leaderboardHint, leaderboardContent);

        // Your stake and points
        Label stakeCaption = GameText.label(assetStore.chipTaglineFont, "Y O U R   S T A K E");
        stakeCaption.setColor(UiKit.MUTED);
        balanceLabel = GameText.label(assetStore.buttonFont, pointsText());
        balanceLabel.setColor(UiKit.GOLD);
        Table centre = new Table();
        centre.add(stakeCaption).padBottom(14).row();
        centre.add(new ChipBadge(assetStore, ChipBadge.selectedKey(), 300)).row();
        centre.add(balanceLabel).padTop(28).row();
        centre.add(muted("points to play with")).row();
        Label minimum = muted(String.format(Locale.US, "Players challenge you for %,d", ChipBadge.amountOf(ChipBadge.selectedKey())));
        centre.add(minimum).padTop(24).row();
        Label hint = muted("Tap a player to challenge them,\ntap the leaderboard for profiles");
        hint.setAlignment(Align.center);
        centre.add(hint).padTop(40);

        Table body = new Table();
        body.add(players).width(sideWidth).expandY().fillY();
        body.add(centre).expand().center();
        body.add(leaderboard).width(sideWidth).expandY().fillY();

        Table root = new Table();
        root.setFillParent(true);
        root.top();
        root.add(buildTopBar()).expandX().fillX().height(TOP_BAR_HEIGHT).row();
        root.add(new Image(UiKit.rule())).expandX().fillX().height(3).row();
        root.add(body).expand().fill().pad(36, 40, 40, 40);

        stage.addActor(backgroundImage);
        stage.addActor(root);
    }

    /**
     * Back on the left, the title in the middle, Share on the right.
     */
    private Table buildTopBar() {
        Table bar = new Table();
        bar.setBackground(UiKit.bar());
        bar.pad(0, 44, 0, 44);

        Label back = GameText.label(assetStore.buttonFont, "< BACK");
        back.setColor(UiKit.GOLD);
        tappable(back, () -> ((Piga21Game) game).goBack());

        Label title = GameText.label(assetStore.titleFont, "PLAY ONLINE");

        // The art is a round share icon followed by a "SHARE" pill; only the icon is used
        TextureRegion shareArt = assetStore.otherComponents.findRegion("share");
        Image shareIcon = new Image(new TextureRegion(shareArt, 0, 0, shareArt.getRegionHeight(), shareArt.getRegionHeight()));
        Label shareText = GameText.label(assetStore.buttonFont, "SHARE");
        shareText.setColor(UiKit.GOLD);
        Table share = new Table();
        share.add(shareIcon).size(84, 84).padRight(16);
        share.add(shareText);
        tappable(share, () -> commonUtilInterface.shareApp(
            "Play Piga21 against me! Download the game and challenge me online.", "Piga21 challenge"));

        Table left = new Table();
        left.add(back);
        bar.add(left).left().expandX().uniformX();
        bar.add(title).center();
        Table right = new Table();
        right.add(share);
        bar.add(right).right().expandX().uniformX();
        return bar;
    }

    /**
     * A titled panel with a scrolling list.
     */
    private Table section(String title, Label caption, Table content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, true);

        Table header = new Table();
        Label titleLabel = GameText.label(assetStore.chipNameFont, title);
        header.add(titleLabel).left().expandX();
        header.add(caption).right();

        Table section = new Table();
        section.setBackground(UiKit.section());
        section.top();
        section.add(header).expandX().fillX().padBottom(14).row();
        section.add(new Image(UiKit.rule())).expandX().fillX().height(2).padBottom(18).row();
        section.add(scroll).expand().fill().top();
        return section;
    }

    private Label muted(String text) {
        Label label = GameText.label(assetStore.listFont, text);
        label.setColor(UiKit.MUTED);
        return label;
    }

    private String pointsText() {
        WalletClient wallet = WalletClient.get();
        return wallet.isLoaded() ? String.format(Locale.US, "%,d", wallet.getBalance()) : "--";
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
        // Points change when a game takes or pays a stake
        if (WalletClient.get().handle(message)) {
            balanceLabel.setText(pointsText());
            return;
        }
        if (message.has("status") && "error".equals(message.get("status").getAsString())) {
            commonUtilInterface.showToast(message.get("message").getAsString());
            return;
        }

        String action = message.has("action") ? message.get("action").getAsString() : "";

        switch (action) {
            case "online_players" -> populatePlayersList(message.getAsJsonArray("players"));
            case "player_profile" -> showPlayerProfile(message);
            case "leaderboard" -> populateLeaderboard(message.getAsJsonArray("players"),
                message.has("me") && message.get("me").isJsonObject() ? message.getAsJsonObject("me") : null);
            case "game_request" -> {
                pendingRequestId = message.get("request_id").getAsString();
                String fromName = message.has("from_name") ? message.get("from_name").getAsString() : "A player";
                int amount = message.has("amount") ? message.get("amount").getAsInt() : 0;
                int fee = message.has("fee") ? message.get("fee").getAsInt() : ChipBadge.feeFor(amount);
                boolean rematch = message.has("rematch") && message.get("rematch").getAsBoolean();
                acceptGameRequestDialog(rematch ? fromName + " wants a rematch!" : fromName + " challenges you!", String.format(Locale.US, "Stake: %,d points  ·  Fee: %,d", amount, fee));
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
     * Server-ranked board: games won this week, ties going to whoever got there first.
     * If you're not in the top list, your own rank is shown underneath.
     */
    private void populateLeaderboard(JsonArray players, JsonObject me) {
        leaderboardContent.clearChildren();
        int myId = Util.getUserId();
        boolean meListed = false;

        if (players.isEmpty()) {
            leaderboardContent.add(muted("No wins yet this week")).padTop(60).row();
        }

        for (int i = 0; i < players.size(); i++) {
            JsonObject player = players.get(i).getAsJsonObject();
            boolean isMe = player.get("id").getAsInt() == myId;
            meListed |= isMe;
            addLeaderboardRow(player, isMe);
        }

        if (!meListed && me != null && !me.get("rank").isJsonNull()) {
            addLeaderboardRow(me, true);
        }
    }

    private void addLeaderboardRow(JsonObject player, boolean isMe) {
        int rank = player.get("rank").getAsInt();
        TextureRegion medal = switch (rank) {
            case 1 -> goldTR;
            case 2 -> silverTR;
            case 3 -> bronzeTR;
            default -> null;
        };
        Actor rankCell;
        if (medal != null) {
            rankCell = new Image(medal);
        } else {
            Label number = GameText.label(assetStore.chipNameFont, "#" + rank);
            number.setColor(UiKit.MUTED);
            number.setAlignment(Align.center);
            rankCell = number;
        }

        String name = player.get("name").getAsString();
        Label nameLabel = GameText.label(assetStore.messageFont, isMe ? name + "  (you)" : name);
        nameLabel.setEllipsis(true);
        if (isMe) nameLabel.setColor(UiKit.GOLD);

        Table row = new Table();
        row.setBackground(isMe ? UiKit.cardPressed() : UiKit.card());
        row.add(rankCell).size(64, 64).padRight(18);
        row.add(nameLabel).left().expandX().fillX().minWidth(0);
        int playerId = player.get("id").getAsInt();
        tappable(row, () -> {
            if (isMe) {
                showMyProfile();
            } else {
                requestPlayerProfile(playerId);
            }
        });
        leaderboardContent.add(row).expandX().fillX().height(96).padBottom(12).row();
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
        int stake = message.has("stake") ? message.get("stake").getAsInt() : ChipBadge.amountOf(ChipBadge.KEYS[0]);
        Runnable challenge = online && !inGame ? () -> challenge(playerId, ProfileDialog.displayName(player), stake) : null;
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

    private void populatePlayersList(JsonArray playersArray) {
        playersContent.clearChildren();

        Map<String, Boolean> newKnownPlayers = new HashMap<>();
        int userId = Util.getUserId();

        for (int i = 0; i < playersArray.size(); i++) {
            JsonObject player = playersArray.get(i).getAsJsonObject();
            int playerId = player.get("id").getAsInt();
            if (playerId == userId) continue; // not ourselves

            String displayName = ProfileDialog.displayName(player);
            int betAmount = player.get("bet_amount").getAsInt();

            Label nameLabel = GameText.label(assetStore.messageFont, displayName);
            nameLabel.setEllipsis(true);

            // Their chosen stake, on the right
            Label stakeAmount = GameText.label(assetStore.buttonFont, String.format(Locale.US, "%,d", betAmount));
            stakeAmount.setColor(UiKit.GOLD);
            Label stakeCaption = GameText.label(assetStore.chipTaglineFont, "STAKE");
            stakeCaption.setColor(UiKit.MUTED);
            Table stake = new Table();
            stake.add(stakeAmount).right().row();
            stake.add(stakeCaption).right();

            // Tapping the row challenges them for their stake
            Table row = new Table();
            row.setBackground(UiKit.card());
            row.add(UiKit.initialBadge(assetStore, displayName)).size(72, 72).padRight(18);
            row.add(nameLabel).left().expandX().fillX().minWidth(0);
            row.add(stake).right().padLeft(16).padRight(8);
            tappable(row, () -> challenge(playerId, displayName, betAmount));

            // Fade in players who just came online
            if (!knownPlayers.containsKey(displayName)) {
                row.getColor().a = 0f;
                row.addAction(Actions.fadeIn(0.5f));
            }

            playersContent.add(row).expandX().fillX().height(108).padBottom(12).row();
            newKnownPlayers.put(displayName, true);
        }

        // The list can contain only us, which we skip, so check what was actually shown
        if (newKnownPlayers.isEmpty()) {
            Label empty = muted("No one else is online right now.\nShare Piga21 to bring friends in.");
            empty.setAlignment(Align.center);
            playersContent.add(empty).padTop(60).row();
        }
        onlineCountLabel.setText(newKnownPlayers.isEmpty() ? "" : newKnownPlayers.size() + " online");

        knownPlayers.clear();
        knownPlayers.putAll(newKnownPlayers);
    }

    private void acceptGameRequestDialog(String title, String detail) {
        timeCountLabel = GameText.label(assetStore.titleFont, String.valueOf(Util.CHALLENGE_SECONDS));
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
        WalletClient.get().refresh();

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
        timeCountLabel = GameText.label(assetStore.titleFont, String.valueOf(Util.CHALLENGE_SECONDS));
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

    /**
     * Challenge a player for the stake they chose, if we have the points for it and its service fee.
     */
    private void challenge(int opponentId, String opponentName, int stake) {
        WalletClient wallet = WalletClient.get();
        if (wallet.isLoaded() && wallet.getBalance() < stake + ChipBadge.feeFor(stake)) {
            commonUtilInterface.showToast("You don't have enough points to request a game");
            return;
        }
        initiateGameRequest(opponentId, opponentName, stake);
    }

    private void initiateGameRequest(int opponentId, String opponentName, int amount) {
        challengedName = opponentName;
        Map<String, Object> data = new HashMap<>();
        data.put("action", "request_game");
        data.put("opponent_id", opponentId);
        data.put("amount", amount);

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
