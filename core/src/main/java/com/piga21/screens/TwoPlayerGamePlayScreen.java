package com.piga21.screens;

import static com.piga21.utils.Util.blackJackEffect;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.piga21.AssetStore;
import com.piga21.BackHandler;
import com.piga21.Piga21Game;
import com.piga21.cards.Card;
import com.piga21.sockets.WebSocketHandler;
import com.piga21.utils.ChipBadge;
import com.piga21.utils.CommonGameFunctions;
import com.piga21.utils.CommonUtilInterface;
import com.piga21.utils.GameText;
import com.piga21.utils.OverlayContainer;
import com.piga21.utils.Settings;
import com.piga21.utils.UiKit;
import com.piga21.wallet.WalletClient;
import com.piga21.utils.Util;

/**
 * Two-player (online) blackjack. The server owns the game: this screen sends the player's
 * actions and draws whatever state the server sends back, from this player's point of view.
 * Players can only hit or stand. The one who busts loses; if both stand the higher hand
 * wins, and equal hands are a draw where no points change hands.
 */
public class TwoPlayerGamePlayScreen implements Screen, BackHandler {
    private static final String HIDDEN_CARD = "HIDDEN";
    private static final float CARD_WIDTH = 165, CARD_HEIGHT = 230; // the card art is 132x184
    private static final float CARD_SPACING = 14;
    private static final float TOP_BAR_HEIGHT = 120;
    private static final float TIMER_WIDTH = 520;
    private static final float TURN_SECONDS = 30; // the server's turn length, for the timer bar
    private static final float BACK_PRESS_WINDOW_SECONDS = 2f;
    private static final int LOW_TIME_SECONDS = 5;
    private static final Color LOW_TIME_TINT = Color.valueOf("#FF5A4A");
    private static final Color WIN_COLOR = Color.valueOf("#5BD47A");

    private final Game game;
    public WebSocketHandler webSocketHandler;
    public CommonUtilInterface commonUtilInterface;
    AssetStore assetStore;
    public Stage stage;

    private TextureRegion deckTR;
    private Image deckImage;
    private Label myScoreLabel, opponentScoreLabel, turnLabel, timerSecondsLabel, stakeLabel, pointsLabel;
    private Stack myScore, opponentScore;
    private Image timerFill;
    private Table timerRow, actionRow;
    private TextButton hitButton, standButton;

    private final List<Image> myCardImages = new ArrayList<>();
    private final List<String> myCardsShown = new ArrayList<>();
    private final List<Image> opponentCardImages = new ArrayList<>();
    private final List<String> opponentCardsShown = new ArrayList<>();

    private final String gameId;
    private final String opponentName;
    private final String opponentId;
    private final ExtendViewport extendViewport;
    private final int stakeAmount;

    // Rematch after the game: the request we sent, or the one the opponent sent us
    private String rematchRequestId;
    private String incomingRematchId;
    private boolean awaitingRematch = false;
    private float rematchSecondsLeft = 0;
    private String serviceNote = "";
    private Label rematchStatusLabel;
    private TextButton rematchButton;
    private boolean showingAccept = false;
    private JsonObject gameView;
    private boolean awaitingServer = false;
    private boolean gameOver = false;
    private boolean menuShowing = false;
    private boolean leaveArmed = false;
    private float turnSecondsLeft = 0;
    private float backPressedAt = -1;
    private float elapsed = 0;


    /**
     * @param gameStartedMessage the server's "game_started" message
     */
    public TwoPlayerGamePlayScreen(Game game, AssetStore assetStore, ExtendViewport extendViewport, WebSocketHandler webSocketHandler, CommonUtilInterface commonUtilInterface,
                                   String gameStartedMessage) {
        this.game = game;
        this.extendViewport = extendViewport;
        this.assetStore = assetStore;
        this.webSocketHandler = webSocketHandler;
        this.commonUtilInterface = commonUtilInterface;
        stage = new Stage(extendViewport);
        Gdx.input.setInputProcessor(stage);

        JsonObject started = JsonParser.parseString(gameStartedMessage).getAsJsonObject();
        gameId = started.get("game_id").getAsString();
        opponentName = started.has("opponent_name") ? started.get("opponent_name").getAsString() : "Opponent";
        opponentId = started.get("opponent_id").getAsString();
        stakeAmount = started.getAsJsonObject("game").get("amount").getAsInt();
        Util.storeCurrentGameId(gameId);

        // From the red theme, so the deck and face-down cards always match the table
        deckTR = assetStore.backgroundComponents.findRegion("deck");
        buildTable();
        listenToServer();

        applyGameView(started.getAsJsonObject("game"));
    }

    // ---- Layout ----

    private void buildTable() {
        float width = stage.getWidth(), height = stage.getHeight();

        Image background = assetStore.redBackground();
        background.setSize(width, height);
        Image vignette = new Image(UiKit.vignette());
        vignette.setSize(width, height);
        vignette.setTouchable(Touchable.disabled);
        stage.addActor(background);
        stage.addActor(vignette);

        // Top bar: who you're playing, the stake, and the menu
        Table bar = new Table();
        bar.setBackground(UiKit.bar());
        bar.pad(0, 40, 0, 40);
        Label vs = GameText.label(assetStore.chipTaglineFont, "PLAYING AGAINST");
        vs.setColor(UiKit.MUTED);
        Table names = new Table();
        names.add(vs).left().row();
        names.add(GameText.label(assetStore.buttonFont, opponentName)).left();
        Table opponent = new Table();
        opponent.add(UiKit.initialBadge(assetStore, opponentName)).size(72, 72).padRight(18);
        opponent.add(names).left();

        stakeLabel = GameText.label(assetStore.buttonFont, "");
        stakeLabel.setColor(UiKit.GOLD);
        Table stake = new Table();
        stake.setBackground(UiKit.pill());
        stake.add(stakeLabel);

        TextureRegion settingsArt = assetStore.otherComponents.findRegion("button_settings");
        // The art is a round gear icon followed by a "SETTINGS" pill; only the icon is used
        Image menu = new Image(new TextureRegion(settingsArt, 0, 0, settingsArt.getRegionHeight(), settingsArt.getRegionHeight()));
        tappable(menu, this::showMenu);

        Table left = new Table();
        left.add(opponent);
        bar.add(left).left().expandX().uniformX();
        bar.add(stake).center();
        Table right = new Table();
        right.add(menu).size(88, 88);
        bar.add(right).right().expandX().uniformX();
        bar.setBounds(0, height - TOP_BAR_HEIGHT, width, TOP_BAR_HEIGHT);
        stage.addActor(bar);
        Image rule = new Image(UiKit.rule());
        rule.setBounds(0, height - TOP_BAR_HEIGHT - 3, width, 3);
        stage.addActor(rule);

        // The deck the cards come from, on the left
        deckImage = new Image(deckTR);
        float deckHeight = CARD_HEIGHT * 1.06f;
        deckImage.setSize(deckHeight * deckTR.getRegionWidth() / deckTR.getRegionHeight(), deckHeight);
        deckImage.setPosition(60, middleY() - deckHeight / 2);
        stage.addActor(deckImage);

        // This game's stake chip, on the right (set by the challenger, not this player's pick)
        ChipBadge chip = new ChipBadge(assetStore, ChipBadge.keyForAmount(stakeAmount), 190);
        chip.setPosition(width - chip.getWidth() - 60, middleY() - chip.getHeight() / 2);
        stage.addActor(chip);

        // Score pills beside each hand: gold for you, maroon for them
        myScore = scorePill("scoreMine", UiKit.GOLD, UiKit.GOLD_DARK, UiKit.BUTTON_TEXT);
        myScoreLabel = (Label) myScore.getChildren().peek();
        opponentScore = scorePill("scoreTheirs", UiKit.MAROON, UiKit.GOLD, UiKit.GOLD);
        opponentScoreLabel = (Label) opponentScore.getChildren().peek();
        stage.addActor(myScore);
        stage.addActor(opponentScore);

        // Whose turn, with a bar that runs down
        turnLabel = GameText.label(assetStore.titleFont, "");
        turnLabel.setAlignment(Align.center);
        Image timerTrack = new Image(UiKit.circle("barTrack", new Color(1, 1, 1, 0.12f), null, 4, 0));
        timerFill = new Image(UiKit.circle("barFill", UiKit.GOLD, null, 4, 0));
        Group timerBar = new Group();
        timerBar.setSize(TIMER_WIDTH, 8);
        timerTrack.setSize(TIMER_WIDTH, 8);
        timerFill.setSize(TIMER_WIDTH, 8);
        timerBar.addActor(timerTrack);
        timerBar.addActor(timerFill);
        timerSecondsLabel = GameText.label(assetStore.chipNameFont, "");
        timerRow = new Table();
        timerRow.add(turnLabel).colspan(2).padBottom(10).row();
        timerRow.add(timerBar).size(TIMER_WIDTH, 8).padRight(18);
        timerRow.add(timerSecondsLabel).width(70).left();
        timerRow.pack();
        timerRow.setPosition(width / 2 - timerRow.getWidth() / 2, middleY() - timerRow.getHeight() / 2);
        timerRow.setTouchable(Touchable.disabled);
        stage.addActor(timerRow);

        // Actions, along the bottom
        hitButton = UiKit.button(assetStore, "HIT", UiKit.Style.PRIMARY, () -> act("hit", () -> playSound(assetStore.hitSound)));
        standButton = UiKit.button(assetStore, "STAND", UiKit.Style.SECONDARY, () -> act("stand", () -> playSound(assetStore.bell)));
        actionRow = new Table();
        stage.addActor(actionRow);
        hideActionButtons();
    }

    /**
     * A score in a rounded pill, e.g. "16" or "9+" for a hand with a hidden card.
     */
    private Stack scorePill(String key, Color fill, Color ring, Color text) {
        Stack pill = new Stack();
        pill.add(new Image(UiKit.circle(key, fill, ring, 40, 4)));
        Label label = GameText.label(assetStore.buttonFont, "0");
        label.setColor(text);
        label.setAlignment(Align.center);
        pill.add(label);
        pill.setSize(124, 80);
        return pill;
    }

    private float myHandY() {
        return 190;
    }

    private float opponentHandY() {
        return stage.getHeight() - TOP_BAR_HEIGHT - 40 - CARD_HEIGHT;
    }

    private float middleY() {
        return (myHandY() + CARD_HEIGHT + opponentHandY()) / 2;
    }

    /**
     * Hands are centred, so a hand of n cards starts this far from the left edge.
     */
    private float handLeftEdge(int cards) {
        float totalWidth = cards * CARD_WIDTH + Math.max(0, cards - 1) * CARD_SPACING;
        return (stage.getWidth() - totalWidth) / 2;
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
                actor.addAction(Actions.scaleTo(1.08f, 1.08f, 0.08f));
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

    // ---- Playing ----

    private void act(String type, Runnable feedback) {
        if (awaitingServer || gameOver || !isMyTurn()) return;
        feedback.run();
        sendGameAction(type);
    }

    private void sendGameAction(String type) {
        if (!webSocketHandler.isConnected()) {
            commonUtilInterface.showToast("Not connected to the game server");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("action", "game_action");
        data.put("type", type);
        data.put("game_id", gameId);
        webSocketHandler.sendMessage(new Gson().toJson(data));

        // One action at a time until the server answers
        awaitingServer = true;
        hideActionButtons();
    }

    private void listenToServer() {
        webSocketHandler.setMessageListener(message -> Gdx.app.postRunnable(() -> {
            try {
                handleServerMessage(message, JsonParser.parseString(message).getAsJsonObject());
            } catch (Exception e) {
                Gdx.app.error("TwoPlayerGame", "Failed to handle message: " + message, e);
            }
        }));
    }

    private void handleServerMessage(String raw, JsonObject message) {
        // Points change when the game takes or pays the stake
        if (WalletClient.get().handle(message)) {
            if (pointsLabel != null) pointsLabel.setText(resultDetails());
            return;
        }
        if (message.has("status") && "error".equals(message.get("status").getAsString())) {
            awaitingServer = false;
            if (gameView != null) updateButtons();
            commonUtilInterface.showToast(message.get("message").getAsString());
            if (awaitingRematch) {
                // Opponent left, is in another game, or someone can't cover the stake
                leaveToLobby();
            }
            return;
        }

        String action = message.has("action") ? message.get("action").getAsString() : "";
        if (gameOver && handleRematchMessage(action, raw, message)) {
            return;
        }
        if (message.has("game_id") && !gameId.equals(message.get("game_id").getAsString())) {
            return;
        }

        switch (action) {
            case "game_update", "game_over" -> applyGameView(message.getAsJsonObject("game"));
            case "opponent_disconnected" -> commonUtilInterface.showToast(
                opponentName + " disconnected. Waiting " + message.get("grace_seconds").getAsInt() + "s for them to return...");
            case "opponent_reconnected" -> commonUtilInterface.showToast(opponentName + " is back");
            default -> { }
        }
    }

    /**
     * Draw the table from the server's view of the game.
     */
    private void applyGameView(JsonObject view) {
        if (gameOver) return;

        gameView = view;
        awaitingServer = false;

        boolean myCardAdded = syncHand(myCardImages, myCardsShown, view.getAsJsonArray("my_cards"), myHandY());
        boolean opponentCardAdded = syncHand(opponentCardImages, opponentCardsShown, view.getAsJsonArray("opponent_cards"), opponentHandY());
        if (myCardAdded || opponentCardAdded) {
            playSound(assetStore.hitSound);
        }

        myScoreLabel.setText(String.valueOf(view.get("my_score").getAsInt()));
        boolean opponentHidden = opponentCardsShown.contains(HIDDEN_CARD);
        opponentScoreLabel.setText(view.get("opponent_score").getAsInt() + (opponentHidden ? "+" : ""));
        placeScore(myScore, myCardsShown.size(), myHandY());
        placeScore(opponentScore, opponentCardsShown.size(), opponentHandY());

        int multiplier = view.get("multiplier").getAsInt();
        stakeLabel.setText(String.format(Locale.US, "STAKE  %,d", view.get("stake").getAsInt()) + (multiplier > 1 ? "  ·  DOUBLED" : ""));

        turnSecondsLeft = view.get("turn_seconds_left").getAsFloat();
        updateTurnLabel();
        updateButtons();

        if ("finished".equals(view.get("status").getAsString())) {
            gameOver = true;
            hideActionButtons();
            timerRow.addAction(Actions.fadeOut(0.3f));
            JsonObject result = view.getAsJsonObject("result");
            // Let the last card and the revealed hole card land before the result
            stage.addAction(Actions.sequence(Actions.delay(1.5f), Actions.run(() -> showGameOver(result))));
        }
    }

    /**
     * The score sits just left of its hand, level with the middle of the cards.
     */
    private void placeScore(Stack score, int cards, float handY) {
        float x = handLeftEdge(cards) - score.getWidth() - 30;
        float y = handY + CARD_HEIGHT / 2 - score.getHeight() / 2;
        score.clearActions();
        score.addAction(Actions.moveTo(x, y, 0.3f));
    }

    /**
     * Brings the cards on screen in line with the server's list: flips revealed hole cards
     * in place, deals new ones from the deck and re-centres the hand.
     *
     * @return true if a new card was dealt
     */
    private boolean syncHand(List<Image> images, List<String> shown, JsonArray cards, float y) {
        List<String> target = new ArrayList<>();
        for (JsonElement card : cards) target.add(card.getAsString());

        while (shown.size() > target.size()) {
            images.remove(images.size() - 1).remove();
            shown.remove(shown.size() - 1);
        }

        for (int i = 0; i < shown.size(); i++) {
            if (!shown.get(i).equals(target.get(i))) {
                revealCard(images.get(i), target.get(i));
                shown.set(i, target.get(i));
            }
        }

        float startX = handLeftEdge(target.size());
        for (int i = 0; i < images.size(); i++) {
            images.get(i).addAction(Actions.moveTo(startX + i * (CARD_WIDTH + CARD_SPACING), y, 0.3f));
        }

        boolean added = false;
        for (int i = shown.size(); i < target.size(); i++) {
            Image cardImage = new Image(cardRegion(target.get(i)));
            cardImage.setSize(CARD_WIDTH, CARD_HEIGHT);
            cardImage.setPosition(deckImage.getX(), deckImage.getY());
            cardImage.getColor().a = 0;
            stage.addActor(cardImage);
            cardImage.addAction(Actions.sequence(
                Actions.delay((i - shown.size()) * 0.2f),
                Actions.parallel(
                    Actions.moveTo(startX + i * (CARD_WIDTH + CARD_SPACING), y, 0.5f, Interpolation.pow2Out),
                    Actions.fadeIn(0.3f)
                )
            ));
            images.add(cardImage);
            added = true;
        }
        shown.clear();
        shown.addAll(target);

        return added;
    }

    private void revealCard(Image cardImage, String cardName) {
        cardImage.setOrigin(CARD_WIDTH / 2, CARD_HEIGHT / 2);
        cardImage.addAction(Actions.sequence(
            Actions.scaleTo(0f, 1f, 0.15f),
            Actions.run(() -> cardImage.setDrawable(new TextureRegionDrawable(cardRegion(cardName)))),
            Actions.scaleTo(1f, 1f, 0.15f)
        ));
    }

    private TextureRegion cardRegion(String cardName) {
        return HIDDEN_CARD.equals(cardName) ? deckTR : assetStore.getTextureRegion(Card.fromString(cardName));
    }

    private boolean isMyTurn() {
        return gameView != null && gameView.get("is_my_turn").getAsBoolean();
    }

    private void updateButtons() {
        hideActionButtons();
        if (gameOver || awaitingServer || !isMyTurn()) return;

        actionRow.add(hitButton).width(320).height(120).padRight(32);
        actionRow.add(standButton).width(320).height(120);
        actionRow.pack();
        actionRow.setPosition(stage.getWidth() / 2 - actionRow.getWidth() / 2, 40);
        actionRow.getColor().a = 0;
        actionRow.addAction(Actions.fadeIn(0.2f));
    }

    private void hideActionButtons() {
        actionRow.clearChildren();
    }

    private void updateTurnLabel() {
        // Both players see the clock, so the waiting player knows how long the other has left
        int seconds = (int) Math.ceil(turnSecondsLeft);
        if (gameOver || gameView == null) {
            turnLabel.setText("");
            timerSecondsLabel.setText("");
            return;
        }

        boolean mine = isMyTurn();
        turnLabel.setText(mine ? "YOUR TURN" : opponentName.toUpperCase(Locale.US) + "'S TURN");
        turnLabel.setColor(mine ? UiKit.GOLD : Color.WHITE);
        timerSecondsLabel.setText(seconds + "s");
        // Last few seconds turn red
        boolean low = seconds <= LOW_TIME_SECONDS;
        timerFill.setColor(low ? LOW_TIME_TINT : Color.WHITE);
        timerSecondsLabel.setColor(low ? LOW_TIME_TINT : Color.WHITE);
        timerFill.setWidth(Math.max(8, TIMER_WIDTH * Math.min(1f, turnSecondsLeft / TURN_SECONDS)));
    }

    private void playSound(Sound sound) {
        if (Settings.getSoundSetting() && sound != null) {
            sound.play();
        }
    }

    // ---- Menu ----

    /**
     * Sounds, leaving the table (forfeits the hand, so it asks twice) and back to the game.
     */
    private void showMenu() {
        if (gameOver) return;
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.8f);
        leaveArmed = false;

        Table panel = new Table();
        panel.setBackground(UiKit.panel());
        panel.add(GameText.title(assetStore, "MENU")).colspan(2).padBottom(30).row();

        TextButton[] sound = new TextButton[1];
        sound[0] = UiKit.button(assetStore, Settings.getSoundSetting() ? "ON" : "OFF", UiKit.Style.SMALL, () -> {
            Settings.toggleSoundSettings(!Settings.getSoundSetting());
            sound[0].setText(Settings.getSoundSetting() ? "ON" : "OFF");
        });
        panel.add(GameText.label(assetStore.messageFont, "Game sounds")).left().width(420).padBottom(24);
        panel.add(sound[0]).width(190).height(76).right().padBottom(24).row();

        Label leaveHint = GameText.label(assetStore.chipTaglineFont, String.format(Locale.US, "Leaving forfeits this hand (%,d points + %,d service points)", currentStake(), ChipBadge.feeFor(currentStake())));
        leaveHint.setColor(UiKit.MUTED);
        TextButton[] leave = new TextButton[1];
        leave[0] = UiKit.button(assetStore, "LEAVE TABLE", UiKit.Style.SECONDARY, () -> {
            if (!leaveArmed) {
                leaveArmed = true;
                leave[0].setText("TAP AGAIN TO LEAVE");
                return;
            }
            closeMenu();
            forfeit();
        });
        panel.add(leave[0]).colspan(2).width(610).height(100).padTop(10).row();
        panel.add(leaveHint).colspan(2).padTop(8).padBottom(26).row();
        panel.add(UiKit.button(assetStore, "BACK TO GAME", UiKit.Style.PRIMARY, this::closeMenu))
            .colspan(2).width(610).height(110).row();

        panel.pack();
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() / 2 - panel.getHeight() / 2);
        overlay.addActor(panel);
        CommonGameFunctions.showOverlay();
        menuShowing = true;
    }

    private void closeMenu() {
        CommonGameFunctions.hideOverlayScreen();
        menuShowing = false;
    }

    private int currentStake() {
        return gameView != null ? gameView.get("stake").getAsInt() : stakeAmount;
    }

    private void forfeit() {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "end_game");
        data.put("game_id", gameId);
        webSocketHandler.sendMessage(new Gson().toJson(data));
        // The server replies with game_over, which shows the result
    }

    // ---- Result ----

    private String resultMessage(JsonObject result) {
        String outcome = result.get("outcome").getAsString();
        String reason = result.get("reason").getAsString();
        boolean won = "win".equals(outcome);

        return switch (reason) {
            case "bust" -> won ? opponentName + " busted!" : "You busted!";
            case "blackjack" -> "push".equals(outcome) ? "You both have blackjack, so it's a draw" : (won ? "Blackjack!" : opponentName + " has blackjack");
            case "surrender" -> won ? opponentName + " surrendered" : "You surrendered";
            case "quit" -> won ? opponentName + " left the game" : "You left the game";
            case "disconnect" -> won ? opponentName + " disconnected" : "You were disconnected";
            case "push" -> "Equal hands, so no one wins";
            default -> won ? "Your hand wins" : opponentName + "'s hand wins";
        };
    }

    /**
     * The line under the result: service points taken, then the new balance once it arrives.
     */
    private String resultDetails() {
        String balance = balanceText();
        if (serviceNote.isEmpty()) return balance;
        return balance.isEmpty() ? serviceNote : serviceNote + "   ·   " + balance;
    }

    private String balanceText() {
        WalletClient wallet = WalletClient.get();
        return wallet.isLoaded() ? "Balance  " + wallet.money(wallet.getBalance()) : "";
    }

    public void showGameOver(JsonObject result) {
        String outcome = result.get("outcome").getAsString();
        int chips = result.get("chips").getAsInt();
        boolean won = "win".equals(outcome);
        boolean push = "push".equals(outcome);
        boolean blackjack = won && "blackjack".equals(result.get("reason").getAsString());

        // Points are settled on the server; a "wallet_summary" brings the new total
        if (won) {
            playSound(blackjack ? assetStore.blackJack : assetStore.win);
        }

        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage, 0.82f);

        // Same calm card as the lobby: one heading, both hands, the result, then the choices
        Label title = GameText.label(assetStore.headingFont, push ? "DRAW" : blackjack ? "BLACKJACK!" : won ? "YOU WIN" : "YOU LOSE");
        title.setColor(won ? UiKit.GOLD : UiKit.INK);
        Label reason = GameText.label(assetStore.bodyFont, resultMessage(result));
        reason.setColor(UiKit.SOFT);

        Label vs = GameText.label(assetStore.captionFont, "VS");
        vs.setColor(UiKit.SOFT);
        Table hands = new Table();
        hands.add(finalHand(opponentName, result.get("opponent_score").getAsInt(), gameView.getAsJsonArray("opponent_cards"), !won && !push))
            .expandX().fillX().uniformX();
        hands.add(vs).padLeft(24).padRight(24);
        hands.add(finalHand("You", result.get("my_score").getAsInt(), gameView.getAsJsonArray("my_cards"), won))
            .expandX().fillX().uniformX();

        // Points won or lost after the service points; a draw gives everything back, service points included
        int fee = push || !gameView.has("fee") ? 0 : gameView.get("fee").getAsInt();
        int net = push ? 0 : chips - fee;
        Label chipsLabel = GameText.label(assetStore.valueFont, net == 0 ? "No points change hands"
            : String.format(Locale.US, "%s%,d points", net > 0 ? "+" : "-", Math.abs(net)));
        chipsLabel.setColor(net > 0 ? WIN_COLOR : net < 0 ? LOW_TIME_TINT : UiKit.INK);
        serviceNote = push ? "All points returned" : fee == 0 ? ""
            : String.format(Locale.US, "Includes %,d service points", fee);
        pointsLabel = GameText.label(assetStore.captionFont, resultDetails());
        pointsLabel.setColor(UiKit.SOFT);

        // Rematch asks the same opponent at the same stake; a decline or no answer goes back to the lobby
        TextButton lobby = UiKit.button(assetStore, "LOBBY", UiKit.Style.SECONDARY, this::leaveToLobby);
        rematchButton = UiKit.button(assetStore, "REMATCH", UiKit.Style.PRIMARY, () -> {
            if (awaitingRematch) return;
            playSound(assetStore.betChipInitializer);
            requestRematch();
        });
        rematchButton.setTransform(true);
        Table buttons = new Table();
        buttons.add(lobby).width(420).height(104).padRight(28);
        buttons.add(rematchButton).width(420).height(104);
        rematchStatusLabel = GameText.label(assetStore.captionFont, "");
        rematchStatusLabel.setColor(UiKit.SOFT);
        rematchStatusLabel.setAlignment(Align.center);

        // The outcome sets the mood: a warm glow behind the heading and a tinted result capsule
        Color mood = push ? UiKit.INK : won ? UiKit.GOLD : LOW_TIME_TINT;
        Table heading = new Table();
        heading.add(title).row();
        heading.add(reason).padTop(-2);
        Table glow = new Table();
        glow.add(new Image(UiKit.glow("resultGlow" + outcome, new Color(mood.r, mood.g, mood.b, push ? 0.16f : 0.34f)))).size(980, 300);
        Stack header = new Stack();
        header.add(glow);
        header.add(heading);

        Table outcomeBox = new Table();
        outcomeBox.setBackground(UiKit.tint(outcome, net > 0 ? WIN_COLOR : net < 0 ? LOW_TIME_TINT : UiKit.INK));
        outcomeBox.add(chipsLabel);

        Table panel = new Table();
        panel.setBackground(UiKit.gradientCard("resultCard", Color.valueOf("#5E0B1C"), Color.valueOf("#1D030A"),
            new Color(0.96f, 0.7f, 0.19f, 0.6f), 44, 3, 56, 40));
        panel.add(header).height(150).row();
        panel.add(new Image(UiKit.fadeRule("resultRule", new Color(mood.r, mood.g, mood.b, 0.7f)))).width(760).height(2).padTop(4).padBottom(30).row();
        panel.add(hands).width(1180).padBottom(32).row();
        panel.add(outcomeBox).row();
        panel.add(pointsLabel).padTop(12).padBottom(32).row();
        panel.add(buttons).row();
        panel.add(rematchStatusLabel).padTop(16).row();

        panel.pack();
        panel.setPosition(stage.getWidth() / 2 - panel.getWidth() / 2, stage.getHeight() / 2 - panel.getHeight() / 2);
        panel.setTransform(true);
        panel.setOrigin(Align.center);
        panel.setScale(0.92f);
        panel.addAction(Actions.scaleTo(1f, 1f, 0.35f, Interpolation.swingOut));
        overlay.addActor(panel);
        updateRematchStatus();

        CommonGameFunctions.showOverlay();
        menuShowing = false;

        if (blackjack) {
            blackJackEffect(stage);
        }
    }

    /**
     * One player's hand in the result panel: name and score over the cards, in a tile that is
     * edged in gold for the winner.
     */
    private Table finalHand(String name, int score, JsonArray cards, boolean winner) {
        Label nameLabel = GameText.label(assetStore.bodyFont, name);
        nameLabel.setColor(winner ? UiKit.GOLD : UiKit.INK);
        nameLabel.setEllipsis(true);
        Label scoreLabel = GameText.label(assetStore.bodyFont, String.valueOf(score));
        scoreLabel.setColor(score > 21 ? LOW_TIME_TINT : winner ? UiKit.GOLD : UiKit.INK);
        Table header = new Table();
        header.add(nameLabel).left().expandX().fillX().minWidth(0);
        if (score > 21) {
            Label bust = GameText.label(assetStore.captionFont, "BUST");
            bust.setColor(LOW_TIME_TINT);
            header.add(bust).right().padRight(10);
        }
        header.add(scoreLabel).right();

        Table row = new Table();
        for (Card card : cardsOf(cards)) {
            row.add(new Image(assetStore.getTextureRegion(card))).size(99, 138).padRight(-30);
        }

        Table hand = new Table();
        hand.setBackground(winner ? UiKit.tileMe() : UiKit.tile());
        hand.add(header).expandX().fillX().padTop(6).padBottom(14).row();
        hand.add(row).padRight(30).padBottom(10);
        return hand;
    }

    /**
     * Rematch on the result panel: accept the opponent's rematch if they already asked,
     * otherwise ask them for one at the same stake.
     */
    private void requestRematch() {
        if (!webSocketHandler.isConnected()) {
            commonUtilInterface.showToast("Not connected to the game server");
            leaveToLobby();
            return;
        }

        awaitingRematch = true;
        rematchButton.clearActions();
        rematchButton.setScale(1f);
        rematchButton.setDisabled(true);

        if (incomingRematchId != null) {
            sendRequestAction("accept_request", incomingRematchId);
            rematchSecondsLeft = 0;
            updateRematchStatus();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("action", "request_game");
        data.put("opponent_id", opponentId);
        data.put("amount", stakeAmount);
        data.put("rematch", true);
        webSocketHandler.sendMessage(new Gson().toJson(data));
        updateRematchStatus();
    }

    /**
     * Rematch traffic that arrives on the result screen.
     *
     * @return true if the message was about the rematch
     */
    private boolean handleRematchMessage(String action, String raw, JsonObject message) {
        String requestId = message.has("request_id") ? message.get("request_id").getAsString() : null;

        switch (action) {
            case "request_sent" -> {
                rematchRequestId = requestId;
                rematchSecondsLeft = message.has("expires_in") ? message.get("expires_in").getAsFloat() : 30;
                updateRematchStatus();
            }
            case "game_request" -> {
                if (!opponentId.equals(message.get("from_user").getAsString())) {
                    // Someone else: we're busy with this opponent
                    sendRequestAction("decline_request", requestId);
                    return true;
                }
                incomingRematchId = requestId;
                playSound(assetStore.bell);
                updateRematchStatus();
            }
            case "request_declined" -> {
                if (requestId != null && requestId.equals(rematchRequestId)) {
                    rematchRequestId = null;
                    commonUtilInterface.showToast(opponentName + " declined the rematch");
                    leaveToLobby();
                }
            }
            case "request_expired", "request_canceled" -> {
                if (requestId != null && requestId.equals(rematchRequestId)) {
                    rematchRequestId = null;
                    commonUtilInterface.showToast(opponentName + " didn't accept the rematch");
                    leaveToLobby();
                } else if (requestId != null && requestId.equals(incomingRematchId)) {
                    incomingRematchId = null;
                    if (!awaitingRematch) {
                        commonUtilInterface.showToast(opponentName + " left the table");
                    }
                    updateRematchStatus();
                }
            }
            case "game_started" -> {
                // Rematch accepted (by either side): same opponent, new table
                rematchRequestId = null;
                incomingRematchId = null;
                ((Piga21Game) game).replaceScreen(new TwoPlayerGamePlayScreen(game, assetStore, extendViewport, webSocketHandler, commonUtilInterface, raw));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void updateRematchStatus() {
        if (rematchStatusLabel == null) return;

        // The opponent asked first: Rematch becomes Accept
        setRematchButton(incomingRematchId != null);

        String stake = String.format(Locale.US, "%,d", stakeAmount);
        String text;
        if (awaitingRematch && incomingRematchId != null && rematchRequestId == null) {
            text = "Starting...";
        } else if (awaitingRematch) {
            text = "Waiting for " + opponentName + "...  " + (int) Math.ceil(rematchSecondsLeft) + "s";
        } else if (incomingRematchId != null) {
            text = opponentName + " wants a rematch for " + stake + " points!";
        } else {
            text = "Rematch asks " + opponentName + " to play again for " + stake + " points";
        }
        rematchStatusLabel.setText(text);
    }

    /**
     * Swap the rematch button between REMATCH (ask) and ACCEPT REMATCH (take theirs), which
     * pulses to catch the eye.
     */
    private void setRematchButton(boolean accept) {
        if (accept == showingAccept) return;
        showingAccept = accept;
        rematchButton.setText(accept ? "ACCEPT REMATCH" : "REMATCH");
        rematchButton.setOrigin(Align.center);
        rematchButton.clearActions();
        rematchButton.setScale(1f);
        if (accept && !awaitingRematch) {
            rematchButton.addAction(Actions.forever(Actions.sequence(
                Actions.scaleTo(1.06f, 1.06f, 0.5f),
                Actions.scaleTo(1f, 1f, 0.5f)
            )));
        }
    }

    private void sendRequestAction(String action, String requestId) {
        if (requestId == null || !webSocketHandler.isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("action", action);
        data.put("request_id", requestId);
        webSocketHandler.sendMessage(new Gson().toJson(data));
    }

    private List<Card> cardsOf(JsonArray names) {
        List<Card> cards = new ArrayList<>();
        for (JsonElement name : names) {
            if (!HIDDEN_CARD.equals(name.getAsString())) cards.add(Card.fromString(name.getAsString()));
        }
        return cards;
    }

    private void leaveToLobby() {
        if (rematchRequestId != null) {
            sendRequestAction("cancel_request", rematchRequestId);
        }
        if (incomingRematchId != null) {
            sendRequestAction("decline_request", incomingRematchId);
        }
        rematchRequestId = null;
        incomingRematchId = null;
        awaitingRematch = false;

        CommonGameFunctions.hideOverlayScreen(true);
        menuShowing = false;
        // popScreen disposes this screen
        ((Piga21Game) game).popScreen();
    }

    /**
     * Back mid-game asks for a second press, since leaving forfeits the hand.
     */
    private void handleBack() {
        if (gameOver) {
            leaveToLobby();
            return;
        }

        if (backPressedAt >= 0 && elapsed - backPressedAt <= BACK_PRESS_WINDOW_SECONDS) {
            forfeit();
            return;
        }

        backPressedAt = elapsed;
        commonUtilInterface.showToast("Press back again to leave. You will forfeit this hand.");
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);

        if (!commonUtilInterface.isNetworkAvailable()) {
            commonUtilInterface.showToast("You are offline");
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        elapsed += delta;

        if (!gameOver && gameView != null && turnSecondsLeft > 0) {
            // Server auto-stands at zero; this is just the visible clock
            turnSecondsLeft = Math.max(0, turnSecondsLeft - delta);
            updateTurnLabel();
        }

        if (awaitingRematch && rematchSecondsLeft > 0) {
            rematchSecondsLeft = Math.max(0, rematchSecondsLeft - delta);
            updateRematchStatus();
        }

        stage.act(delta);
        stage.draw();
    }

    @Override
    public boolean onBack() {
        if (!gameOver && menuShowing) {
            closeMenu();
        } else {
            // Leaves after the game, or asks to confirm the forfeit mid-game
            handleBack();
        }
        return true;
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
