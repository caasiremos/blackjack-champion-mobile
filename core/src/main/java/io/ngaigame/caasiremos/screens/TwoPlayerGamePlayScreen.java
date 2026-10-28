package io.ngaigame.caasiremos.screens;

import static io.ngaigame.caasiremos.utils.Settings.showMenuItemDialog;
import static io.ngaigame.caasiremos.utils.Util.blackJackEffect;
import static io.ngaigame.caasiremos.utils.Util.buttonSize;
import static io.ngaigame.caasiremos.utils.Util.layoutButtonsAtBottomRightHalf;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.ngaigame.caasiremos.AssetStore;
import io.ngaigame.caasiremos.BackHandler;
import io.ngaigame.caasiremos.MainBlackJack;
import io.ngaigame.caasiremos.cards.Card;
import io.ngaigame.caasiremos.cards.CardDealer;
import io.ngaigame.caasiremos.points.GamePoint;
import io.ngaigame.caasiremos.sockets.WebSocketHandler;
import io.ngaigame.caasiremos.utils.ChipBadge;
import io.ngaigame.caasiremos.utils.CommonGameFunctions;
import io.ngaigame.caasiremos.utils.GameText;
import io.ngaigame.caasiremos.utils.CommonUtilInterface;
import io.ngaigame.caasiremos.utils.OverlayContainer;
import io.ngaigame.caasiremos.utils.PlayerApi;
import io.ngaigame.caasiremos.utils.Settings;
import io.ngaigame.caasiremos.utils.Util;

/**
 * Two-player (online) blackjack. The server owns the game: this screen sends the player's
 * actions and draws whatever state the server sends back, from this player's point of view.
 * The one who busts loses; if both stand the higher hand wins.
 */
public class TwoPlayerGamePlayScreen implements Screen, BackHandler {
    private static final String HIDDEN_CARD = "HIDDEN";
    private static final float CARD_SPACING = 10;
    private static final float BACK_PRESS_WINDOW_SECONDS = 2f;
    private static final int LOW_TIME_SECONDS = 5;
    private static final Color LOW_TIME_TINT = Color.valueOf("#FF5A4A");

    private final Game game;
    public WebSocketHandler webSocketHandler;
    public CommonUtilInterface commonUtilInterface;
    AssetStore assetStore;
    public Stage stage;
    public GamePoint gamePoint;

    TextureRegion hitTR, standTR, doubleTR, surrenderTR, restartTR, deckTR, backgroundTR, counterBackgroundTR, winDialogTR, looseDialogTR;
    TextureRegion menuTR, exitTR, menusTR, musicOffTR, musicOnTR, rewardButtonTR, shareTR, soundOffTR, soundOnTR;

    Image hitImage, standImage, doubleImage, surrenderImage, restartImage, deckImage, backgroundImage,
        counterBackgroundImagePlayer, counterBackgroundImageOpponent, winDialogImage, looseDialogImage;
    Image menuImage, exitImage, menusImage, musicOffImage, musicOnImage, rewardButtonImage, shareImage, soundsOffImage, soundOnImage;

    Label handCounter, opponentCounter, turnLabel, infoLabel, pointsLabel, myNameLabel, opponentNameTag;

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
    private Label rematchStatusLabel, playersLink;
    private TextureRegion acceptTR;
    private boolean showingAccept = false;
    private static final float RESULT_BUTTON_HEIGHT = 129;
    private JsonObject gameView;
    private boolean awaitingServer = false;
    private boolean gameOver = false;
    private boolean overlayShowing = false;
    private float turnSecondsLeft = 0;
    private float backPressedAt = -1;
    private float elapsed = 0;
    private float cardWidth, cardHeight;


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
        gamePoint = new GamePoint();

        JsonObject started = JsonParser.parseString(gameStartedMessage).getAsJsonObject();
        gameId = started.get("game_id").getAsString();
        opponentName = started.has("opponent_name") ? started.get("opponent_name").getAsString() : "Opponent";
        opponentId = started.get("opponent_id").getAsString();
        stakeAmount = started.getAsJsonObject("game").get("amount").getAsInt();
        Util.storeCurrentGameId(gameId);

        loadRegions();
        buildTable();
        setChip(started.getAsJsonObject("game").get("amount").getAsInt());
        listenToServer();

        applyGameView(started.getAsJsonObject("game"));
    }

    private void loadRegions() {
        hitTR = assetStore.components.findRegion("button_hit");
        standTR = assetStore.components.findRegion("button_stand");
        doubleTR = assetStore.components.findRegion("button_double");
        surrenderTR = assetStore.components.findRegion("button_surrender");
        restartTR = assetStore.otherComponents.findRegion("button_continue");
        acceptTR = assetStore.otherComponents.findRegion("button_accept");
        deckTR = assetStore.components.findRegion("deck");
        menuTR = assetStore.components.findRegion("menu");
        counterBackgroundTR = assetStore.components.findRegion("counter_background");
        backgroundTR = assetStore.components.findRegion("background");
        winDialogTR = assetStore.components.findRegion("dialog_win");
        looseDialogTR = assetStore.components.findRegion("dialog_loose");

        exitTR = assetStore.otherComponents.findRegion("exit");
        menusTR = assetStore.otherComponents.findRegion("menu");
        musicOffTR = assetStore.otherComponents.findRegion("music_off");
        musicOnTR = assetStore.otherComponents.findRegion("music_on");
        rewardButtonTR = assetStore.otherComponents.findRegion("reward_button");
        shareTR = assetStore.otherComponents.findRegion("share");
        soundOffTR = assetStore.otherComponents.findRegion("sound_off");
        soundOnTR = assetStore.otherComponents.findRegion("sound_on");


        TextureRegion anyCard = assetStore.getTextureRegion(Card.ACE_SPADE);
        cardWidth = anyCard.getRegionWidth();
        cardHeight = anyCard.getRegionHeight();
    }

    private void buildTable() {
        backgroundImage = new Image(backgroundTR);
        backgroundImage.setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        stage.addActor(backgroundImage);

        deckImage = new Image(deckTR);
        deckImage.setPosition(20, deckYPosition());
        stage.addActor(deckImage);

        hitImage = new Image(hitTR);
        standImage = new Image(standTR);
        doubleImage = new Image(doubleTR);
        surrenderImage = new Image(surrenderTR);
        restartImage = new Image(restartTR);
        buttonSize(standImage, hitImage, doubleImage, surrenderImage);

        counterBackgroundImagePlayer = new Image(counterBackgroundTR);
        counterBackgroundImageOpponent = new Image(counterBackgroundTR);
        counterBackgroundImagePlayer.setSize(counterBackgroundImagePlayer.getWidth() * 4, counterBackgroundImagePlayer.getHeight() * 4);
        counterBackgroundImageOpponent.setSize(counterBackgroundImageOpponent.getWidth() * 4, counterBackgroundImageOpponent.getHeight() * 4);
        counterBackgroundImagePlayer.setPosition(stage.getWidth() / 2 - counterBackgroundImagePlayer.getWidth() / 2, myHandY() + cardHeight + 12);
        counterBackgroundImageOpponent.setPosition(stage.getWidth() / 2 - counterBackgroundImageOpponent.getWidth() / 2, opponentHandY() - counterBackgroundImageOpponent.getHeight() - 12);

        winDialogImage = new Image(winDialogTR);
        looseDialogImage = new Image(looseDialogTR);

        menuImage = new Image(menuTR);
        exitImage = new Image(exitTR);
        menusImage = new Image(menusTR);
        musicOffImage = new Image(musicOffTR);
        musicOnImage = new Image(musicOnTR);
        rewardButtonImage = new Image(rewardButtonTR);
        shareImage = new Image(shareTR);
        soundsOffImage = new Image(soundOffTR);
        soundOnImage = new Image(soundOnTR);
        menusImage.setSize(menusImage.getWidth() * 3, menusImage.getHeight() * 3);
        menusImage.setPosition(stage.getWidth() - menusImage.getWidth() - 50f, stage.getHeight() - menusImage.getHeight() - 50f);

        stage.addActor(standImage);
        stage.addActor(hitImage);
        stage.addActor(doubleImage);
        stage.addActor(surrenderImage);
        stage.addActor(menusImage);
        stage.addActor(counterBackgroundImagePlayer);
        stage.addActor(counterBackgroundImageOpponent);

        Label.LabelStyle counterStyle = new Label.LabelStyle(assetStore.counterFont, Color.WHITE);
        handCounter = new Label("0", counterStyle);
        opponentCounter = new Label("0", counterStyle);
        stage.addActor(handCounter);
        stage.addActor(opponentCounter);

        Label.LabelStyle ownerStyle = new Label.LabelStyle(assetStore.listFont, Color.WHITE);
        myNameLabel = new Label("YOU", ownerStyle);
        opponentNameTag = new Label(opponentName.toUpperCase(), ownerStyle);
        stage.addActor(myNameLabel);
        stage.addActor(opponentNameTag);

        Label.LabelStyle infoStyle = new Label.LabelStyle(assetStore.listFont, Color.WHITE);
        turnLabel = new Label("", new Label.LabelStyle(assetStore.dialogFont, Color.valueOf("#F4AD31")));
        infoLabel = new Label("", infoStyle);
        stage.addActor(turnLabel);
        stage.addActor(infoLabel);

        hideActionButtons();

        actionButton(hitImage, "hit", () -> playSound(assetStore.hitSound));
        actionButton(standImage, "stand", () -> playSound(assetStore.bell));
        actionButton(doubleImage, "double", () -> {
            playSound(assetStore.hitSound);
            playSound(assetStore.betchipSound);
        });
        actionButton(surrenderImage, "surrender", () -> playSound(assetStore.bell));

        showMenuItemDialog(stage, commonUtilInterface, menusImage, menuImage, musicOffImage, musicOnImage, soundOnImage, soundsOffImage, shareImage, exitImage, rewardButtonImage, overlayShowing);
    }

    /**
     * The chip for this game's stake (set by the challenger), not the one this player picked.
     */
    private void setChip(int amount) {
        ChipBadge chip = new ChipBadge(assetStore, ChipBadge.keyForAmount(amount), 240);
        chip.setPosition(
            stage.getWidth() - chip.getWidth() - 40f,
            (stage.getHeight() - chip.getHeight()) / 2f
        );
        stage.addActor(chip);
    }

    private void actionButton(Image button, String type, Runnable feedback) {
        button.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                event.getListenerActor().addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                event.getListenerActor().addAction(Actions.scaleTo(1f, 1f, 0.1f));
                if (awaitingServer || gameOver || !isMyTurn()) return;

                feedback.run();
                sendGameAction(type);
            }
        });
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
        PlayerApi.applyPlayerUpdate(message);
        if ("balance_updated".equals(action) && pointsLabel != null) {
            pointsLabel.setText(GamePoint.POINTS + gamePoint.getPoints());
        }
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

        handCounter.setText(String.valueOf(view.get("my_score").getAsInt()));
        boolean opponentHidden = opponentCardsShown.contains(HIDDEN_CARD);
        opponentCounter.setText(view.get("opponent_score").getAsInt() + (opponentHidden ? "+" : ""));
        centreOn(handCounter, counterBackgroundImagePlayer);
        centreOn(opponentCounter, counterBackgroundImageOpponent);

        // Whose hand is whose, just right of each hand
        myNameLabel.pack();
        myNameLabel.setPosition(handRightEdge(myCardsShown.size()) + 30, myHandY() + cardHeight / 2 - myNameLabel.getHeight() / 2);
        opponentNameTag.pack();
        opponentNameTag.setPosition(handRightEdge(opponentCardsShown.size()) + 30, opponentHandY() + cardHeight / 2 - opponentNameTag.getHeight() / 2);

        int multiplier = view.get("multiplier").getAsInt();
        infoLabel.setText("vs " + opponentName + "   STAKE " + view.get("stake").getAsInt() + (multiplier > 1 ? "  (DOUBLED)" : ""));
        infoLabel.pack();
        infoLabel.setPosition(40, stage.getHeight() - infoLabel.getHeight() - 40);

        turnSecondsLeft = view.get("turn_seconds_left").getAsFloat();
        updateTurnLabel();
        updateButtons();

        if ("finished".equals(view.get("status").getAsString())) {
            gameOver = true;
            hideActionButtons();
            JsonObject result = view.getAsJsonObject("result");
            // Let the last card and the revealed hole card land before the dialog
            stage.addAction(Actions.sequence(Actions.delay(1.5f), Actions.run(() -> showGameOver(result))));
        }
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

        float totalWidth = target.size() * cardWidth + (target.size() - 1) * CARD_SPACING;
        float startX = (stage.getWidth() - totalWidth) / 2;

        for (int i = 0; i < images.size(); i++) {
            images.get(i).addAction(Actions.moveTo(startX + i * (cardWidth + CARD_SPACING), y, 0.3f));
        }

        boolean added = false;
        for (int i = shown.size(); i < target.size(); i++) {
            Image cardImage = new Image(cardRegion(target.get(i)));
            cardImage.setSize(cardWidth, cardHeight);
            cardImage.setPosition(20, deckYPosition());
            cardImage.getColor().a = 0;
            stage.addActor(cardImage);
            cardImage.addAction(Actions.sequence(
                Actions.delay((i - shown.size()) * 0.2f),
                Actions.parallel(
                    Actions.moveTo(startX + i * (cardWidth + CARD_SPACING), y, 0.5f),
                    Actions.fadeIn(0.5f)
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
        cardImage.setOrigin(cardWidth / 2, cardHeight / 2);
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

        List<Image> buttons = new ArrayList<>();
        buttons.add(hitImage);
        buttons.add(standImage);
        if (gameView.get("can_double").getAsBoolean()) buttons.add(doubleImage);
        if (gameView.get("can_surrender").getAsBoolean()) buttons.add(surrenderImage);

        for (Image button : buttons) button.setVisible(true);
        layoutButtonsAtBottomRightHalf(stage, buttons.toArray(new Image[0]));
    }

    private void hideActionButtons() {
        hitImage.setVisible(false);
        standImage.setVisible(false);
        doubleImage.setVisible(false);
        surrenderImage.setVisible(false);
    }

    private void updateTurnLabel() {
        // Both players see the clock, so the waiting player knows how long the other has left
        int seconds = (int) Math.ceil(turnSecondsLeft);
        if (gameOver || gameView == null) {
            turnLabel.setText("");
        } else if (isMyTurn()) {
            turnLabel.setText("YOUR TURN  " + seconds);
        } else {
            turnLabel.setText(opponentName.toUpperCase() + "'S TURN  " + seconds);
        }
        // Last few seconds turn red (the font is gold, so this tint reads as red)
        turnLabel.setColor(seconds <= LOW_TIME_SECONDS ? LOW_TIME_TINT : Color.WHITE);
        turnLabel.pack();
        boolean mine = isMyTurn();
        int cards = mine ? myCardsShown.size() : opponentCardsShown.size();
        float handY = mine ? myHandY() : opponentHandY();
        float handLeft = stage.getWidth() - handRightEdge(cards);
        turnLabel.setPosition(handLeft - turnLabel.getWidth() - 40, handY + cardHeight / 2 - turnLabel.getHeight() / 2);
    }

    /**
     * Hands are centred, so a hand of n cards ends this far from the left edge.
     */
    private float handRightEdge(int cards) {
        float totalWidth = cards * cardWidth + Math.max(0, cards - 1) * CARD_SPACING;
        return (stage.getWidth() + totalWidth) / 2;
    }

    private void centreOn(Label label, Image background) {
        label.pack();
        label.setPosition(
            background.getX() + background.getWidth() / 2 - label.getWidth() / 2,
            background.getY() + background.getHeight() / 2 - label.getHeight() / 2
        );
    }

    private float myHandY() {
        return 170 + 170 / 4f; // button height + a quarter, same as the classic table
    }

    private float opponentHandY() {
        return stage.getHeight() - 195 - 40;
    }

    private float deckYPosition() {
        return this.stage.getHeight() / 2 - (float) 195 / 2; //195 is the deck height
    }

    private void playSound(Sound sound) {
        if (Settings.getSoundSetting() && sound != null) {
            sound.play();
        }
    }

    private String resultMessage(JsonObject result) {
        String outcome = result.get("outcome").getAsString();
        String reason = result.get("reason").getAsString();
        boolean won = "win".equals(outcome);

        return switch (reason) {
            case "bust" -> won ? opponentName + " busted!" : "You busted!";
            case "blackjack" -> "push".equals(outcome) ? "Both blackjack!" : (won ? "Blackjack!" : opponentName + " has blackjack");
            case "surrender" -> won ? opponentName + " surrendered" : "You surrendered";
            case "quit" -> won ? opponentName + " left the game" : "You left the game";
            case "disconnect" -> won ? opponentName + " disconnected" : "You were disconnected";
            case "push" -> "Push - it's a tie";
            default -> won ? "Your hand wins" : opponentName + "'s hand wins";
        };
    }

    public void showGameOver(JsonObject result) {
        String outcome = result.get("outcome").getAsString();
        int chips = result.get("chips").getAsInt();
        boolean won = "win".equals(outcome);
        boolean blackjack = won && "blackjack".equals(result.get("reason").getAsString());

        // Chips are settled on the server; "balance_updated" brings the new total

        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage);

        Image dialog = won ? winDialogImage : looseDialogImage;
        dialog.setSize(636, 636);
        dialog.setPosition(stage.getWidth() / 2 - dialog.getWidth() / 2, stage.getHeight() / 2 - dialog.getHeight() / 2);
        if (!"push".equals(outcome)) {
            overlay.addActor(dialog);
            GameText.addRibbonText(assetStore, overlay, dialog, won ? "WIN" : "LOSE");
        }

        if (won) {
            playSound(blackjack ? assetStore.blackJack : assetStore.win);
        }

        Label.LabelStyle headlineStyle = new Label.LabelStyle(assetStore.blackJackFont, Color.valueOf("#F4AD31"));
        Label.LabelStyle countStyle = new Label.LabelStyle(assetStore.dialogCounterFont, Color.valueOf("#FFFFFF"));
        Label.LabelStyle chipsStyle = new Label.LabelStyle(assetStore.dialogFont, Color.valueOf("#EB2F23"));
        Label.LabelStyle betStyle = new Label.LabelStyle(assetStore.betAmountDialogFont, Color.valueOf("#8C0303"));

        Label headline = new Label("push".equals(outcome) ? "PUSH" : (blackjack ? "BLACKJACK" : ""), headlineStyle);
        headline.setPosition(overlay.getWidth() / 2 - headline.getWidth() / 2, overlay.getHeight() - headline.getHeight() * 1.5f);

        Label reasonLabel = new Label(resultMessage(result), new Label.LabelStyle(assetStore.listFont, Color.WHITE));
        reasonLabel.pack();
        reasonLabel.setPosition(overlay.getWidth() / 2 - reasonLabel.getWidth() / 2, dialog.getY() + dialog.getHeight() + 20);

        Label opponentLabel = new Label(opponentName, countStyle);
        Label playerLabel = new Label("You", countStyle);
        Label opponentScore = new Label(String.valueOf(result.get("opponent_score").getAsInt()), countStyle);
        Label playerScore = new Label(String.valueOf(result.get("my_score").getAsInt()), countStyle);
        opponentLabel.setPosition(stage.getWidth() / 2 - dialog.getWidth() / 2 + 80, dialog.getHeight() / 2 + 250);
        playerLabel.setPosition(stage.getWidth() * 3 / 5 + 50 - dialog.getWidth() / 4, dialog.getHeight() / 2 + 250);
        opponentScore.setPosition(stage.getWidth() / 2 - dialog.getWidth() / 2 + 120, dialog.getHeight() / 2 + 210);
        playerScore.setPosition(stage.getWidth() * 3 / 5 + 80 - dialog.getWidth() / 4, dialog.getHeight() / 2 + 210);

        pointsLabel = new Label(GamePoint.POINTS + gamePoint.getPoints(), chipsStyle);
        // The chips bar sits a quarter of the way up the dialog art; centre on it whatever the font height
        float chipsBarY = dialog.getY() + dialog.getHeight() * 0.25f;
        pointsLabel.pack();
        pointsLabel.setPosition(overlay.getWidth() / 2 - dialog.getWidth() / 2 + 200, chipsBarY - pointsLabel.getHeight() / 2);
        Label chipsLabel = new Label((chips > 0 ? "+" : "") + chips, betStyle);
        chipsLabel.pack();
        chipsLabel.setPosition(overlay.getWidth() / 2 - dialog.getWidth() / 2 + 124 - chipsLabel.getWidth() / 2, chipsBarY - chipsLabel.getHeight() / 2);

        overlay.addActor(headline);
        if (!blackjack) {
            // The BLACKJACK headline already says it
            overlay.addActor(reasonLabel);
        }
        overlay.addActor(opponentLabel);
        overlay.addActor(playerLabel);
        overlay.addActor(opponentScore);
        overlay.addActor(playerScore);
        overlay.addActor(pointsLabel);
        overlay.addActor(chipsLabel);

        if (blackjack) {
            blackJackEffect(stage);
        }

        CardDealer myCards = new CardDealer(assetStore, overlay.getStage(), myHandY());
        myCards.dealGameOverCards(overlay.getStage(), cardsOf(gameView.getAsJsonArray("my_cards")), myCards.centerOfCenterXPositionRight(hitImage), stage.getHeight() / 2);
        CardDealer opponentCards = new CardDealer(assetStore, overlay.getStage(), myHandY());
        opponentCards.dealGameOverCards(overlay.getStage(), cardsOf(gameView.getAsJsonArray("opponent_cards")), myCards.centerOfCenterXPositionLeft(hitImage) - 200, stage.getHeight() / 2);

        // Continue asks the same opponent for a rematch; a decline or no answer goes back to the lobby
        restartImage.setSize(390, 129); // button_continue is 269x89
        restartImage.setPosition(stage.getWidth() / 2 - restartImage.getWidth() / 2, restartImage.getHeight() / 2);
        restartImage.clearListeners();
        restartImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                restartImage.addAction(Actions.scaleTo(1.2f, 1.2f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                restartImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                if (awaitingRematch) return;
                playSound(assetStore.betChipInitializer);
                requestRematch();
            }
        });
        overlay.addActor(restartImage);

        // Status on the right of Continue, a way out on the left
        rematchStatusLabel = new Label("", new Label.LabelStyle(assetStore.listFont, Color.WHITE));
        overlay.addActor(rematchStatusLabel);

        playersLink = new Label("< Players", new Label.LabelStyle(assetStore.dialogFont, Color.valueOf("#F4B331")));
        playersLink.pack();
        playersLink.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                leaveToLobby();
            }
        });
        overlay.addActor(playersLink);
        updateRematchStatus();

        CommonGameFunctions.showOverlay();
        overlayShowing = true;
    }

    /**
     * Continue on the result screen: accept the opponent's rematch if they already asked,
     * otherwise ask them for one at the same stake.
     */
    private void requestRematch() {
        if (!webSocketHandler.isConnected()) {
            commonUtilInterface.showToast("Not connected to the game server");
            leaveToLobby();
            return;
        }

        awaitingRematch = true;
        restartImage.clearActions();
        restartImage.setScale(1f);
        restartImage.getColor().a = 0.5f;

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
                ((MainBlackJack) game).replaceScreen(new TwoPlayerGamePlayScreen(game, assetStore, extendViewport, webSocketHandler, commonUtilInterface, raw));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void updateRematchStatus() {
        if (rematchStatusLabel == null) return;

        // The opponent asked first: Continue becomes Accept
        setResultButton(incomingRematchId != null);

        String text;
        if (awaitingRematch && incomingRematchId != null && rematchRequestId == null) {
            text = "Starting...";
        } else if (awaitingRematch) {
            text = "Waiting for " + opponentName + "...  " + (int) Math.ceil(rematchSecondsLeft);
        } else if (incomingRematchId != null) {
            text = opponentName + " wants a rematch\nfor " + stakeAmount + "!";
        } else {
            text = "Continue asks " + opponentName + "\nfor a rematch at " + stakeAmount;
        }

        playersLink.setPosition(restartImage.getX() - playersLink.getWidth() - 80, restartImage.getY() + restartImage.getHeight() / 2 - playersLink.getHeight() / 2);
        rematchStatusLabel.setText(text);
        rematchStatusLabel.pack();
        rematchStatusLabel.setPosition(
            restartImage.getX() + restartImage.getWidth() + 60,
            restartImage.getY() + restartImage.getHeight() / 2 - rematchStatusLabel.getHeight() / 2
        );
    }

    /**
     * Swap the result button between CONTINUE (ask for a rematch) and ACCEPT (take theirs),
     * keeping it centred at the same height; the two images have different shapes.
     */
    private void setResultButton(boolean accept) {
        if (accept == showingAccept && restartImage.getHeight() > 0) return;
        showingAccept = accept;

        TextureRegion region = accept ? acceptTR : restartTR;
        restartImage.setDrawable(new TextureRegionDrawable(region));
        restartImage.setSize(RESULT_BUTTON_HEIGHT * region.getRegionWidth() / region.getRegionHeight(), RESULT_BUTTON_HEIGHT);
        restartImage.setOrigin(restartImage.getWidth() / 2, restartImage.getHeight() / 2);
        restartImage.setPosition(stage.getWidth() / 2 - restartImage.getWidth() / 2, RESULT_BUTTON_HEIGHT / 2);

        restartImage.clearActions();
        restartImage.setScale(1f);
        if (accept) {
            // Draw the eye to it
            restartImage.addAction(Actions.forever(Actions.sequence(
                Actions.scaleTo(1.08f, 1.08f, 0.5f),
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
        overlayShowing = false;
        // popScreen disposes this screen
        ((MainBlackJack) game).popScreen();
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
            Map<String, Object> data = new HashMap<>();
            data.put("action", "end_game");
            data.put("game_id", gameId);
            webSocketHandler.sendMessage(new Gson().toJson(data));
            // The server replies with game_over, which shows the result
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
        if (!gameOver && CommonGameFunctions.isOverlayShowing()) {
            // The settings menu, not the result dialog
            CommonGameFunctions.hideOverlayScreen();
            overlayShowing = false;
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
