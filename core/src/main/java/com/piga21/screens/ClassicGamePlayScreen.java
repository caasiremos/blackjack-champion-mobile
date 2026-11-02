package com.piga21.screens;

import static com.piga21.utils.Settings.showMenuItemDialog;
import static com.piga21.utils.Util.blackJackEffect;
import static com.piga21.utils.Util.buttonSize;
import static com.piga21.utils.Util.handCardsCounter;
import static com.piga21.utils.Util.layoutButtonsAtBottomRightHalf;
import static com.piga21.utils.Util.moveDealButton;

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
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.piga21.Piga21Game;
import com.piga21.AssetStore;
import com.piga21.BackHandler;
import com.piga21.cards.Card;
import com.piga21.cards.CardDealer;
import com.piga21.sockets.WebSocketHandler;
import com.piga21.utils.CommonGameFunctions;
import com.piga21.cards.Deck;
import com.piga21.utils.OverlayContainer;
import com.piga21.chips.CardChips;
import com.piga21.hands.DealerHand;
import com.piga21.hands.PlayerHand;
import com.piga21.hands.SplitHand;
import com.piga21.points.GamePoint;
import com.piga21.utils.Settings;
import com.piga21.utils.CommonUtilInterface;
import com.piga21.utils.GameText;
import com.piga21.utils.PlayerApi;
import com.piga21.utils.Util;

public class ClassicGamePlayScreen implements Screen, BackHandler {
    public WebSocketHandler webSocketHandler;
    public CommonUtilInterface commonUtilInterface;
    AssetStore assetStore;
    public Stage stage;
    public Game game;
    public Deck deck;
    public GamePoint gamePoint;
    public PlayerHand playerHand;
    public DealerHand dealerHand;
    public SplitHand splitHand;
    public CardDealer playerHandDealer, dealerHandDealer;

    //Button regions
    TextureRegion hitTR, standTR, splitTR, doubleTR, insureTR, dealTR, strategyTR, pushTR, quitTR, restartTR, resumeTR, settingTR, surrenderTR;

    //Chip regions
    TextureRegion chip_50TR, chip_100TR, chip_250TR, chip_500TR, chip_1000TR; //TR Texture Region

    //Game over regions
    TextureRegion winDialogTR, looseDialogTR, scoresTR, starTR, deckTR, menuTR, counterBackgroundTR, backgroundTR;

    //Other components regions
    TextureRegion chipsTR, exitTR, menusTR, musicOffTR, musicOnTR, restartsTR, rewardButtonTR, shareTR, soundOffTR, soundOnTR;

    Image backgroundImage, deckImage, hitImage, splitImage, doubleImage, standImage, insureImage,
        dealImage, pushImage, strategyImage, settingImage, restartImage, quitImage, resumeImage,
        surrenderImage, menuImage, counterBackgroundImageDealer, counterBackgroundImagePlayer, scoresImage, startImage, winDialogImage, looseDialogImage,
        counterBackgroundImageSplitHandOne, counterBackgroundImageSplitHandTwo, chipsImage, exitImage, menusImage, musicOffImage, musicOnImage, restartsImage, rewardButtonImage, shareImage, soundsOffImage, soundOnImage;

    public Boolean standMainHand = false;
    public Boolean split = false;
    public Boolean insure = false;
    public Boolean doubleBet = false;
    public Boolean isBlackJack = false;
    public Boolean standSplitHand1 = false; //tracks if the player hits stand on the first split stage
    public Boolean standSplitHand2 = false; //tracks if the player hits stand on the second split stage
    boolean overlayShowing = false;
    private boolean roundReported = false;
    boolean betPlaced = false;
    private CardChips lastSelectedChip;

    //original chip positions
    public float chipCenterX;
    public float chipCenterY;
    public int betAmount, currentPoint;
    public float chipDimension = 171;

    public Label pointsLabel, handCounter, dealerCounter, blackJackLabel, splitHandOneCounter, splitHandTwoCounter,
        dealerDialogLabel, playerDialogLabel, dealerDialogCount, playerDialogCount, betAmountLabel, timeCountLabel, opponentNameLabel;
    Label.LabelStyle labelStyle, blackJackLabelStyle, dialogFontLabelStyle, betAmountLabelStyle, timeCountLabelStyle, opponentLabelStyle;

    List<CardChips> cardChips;
    private String pendingRequestId; // store request ID when message arrives


    public ClassicGamePlayScreen(AssetStore assetStore) {
        this.assetStore = assetStore;
    }

    public ClassicGamePlayScreen(Game game, AssetStore assetStore, ExtendViewport extendViewport, WebSocketHandler webSocketHandler, CommonUtilInterface commonUtilInterface) {
        this.assetStore = assetStore;
        stage = new Stage(extendViewport);
        this.game = game;
        Gdx.input.setInputProcessor(stage);
        this.commonUtilInterface = commonUtilInterface;
        this.webSocketHandler = webSocketHandler;

        this.webSocketHandler.setMessageListener(new WebSocketHandler.MessageListener() {
            @Override
            public void onMessage(String message) {
                Gdx.app.postRunnable(() -> {
                    Gdx.app.log("GameScreen", "Received: " + message);

                    try {
                        JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();

                        if (jsonObject.has("request_id") && !jsonObject.get("request_id").isJsonNull()) {
                            String newRequestId = jsonObject.get("request_id").getAsString();

                            if (pendingRequestId == null || !pendingRequestId.equals(newRequestId)) {
                                pendingRequestId = newRequestId;
                                setupAcceptClickListener(); // setup listener only when a new request_id is received
                            }
                        }
                    } catch (Exception e) {
                        Gdx.app.error("GameScreen", "Failed to parse message or missing request_id", e);
                    }
                });
            }
        });


        chipCenterX = stage.getWidth() - 350;
        chipCenterY = (stage.getHeight() / 2) - (chipDimension / 2);

        hitTR = assetStore.components.findRegion("button_hit");
        standTR = assetStore.components.findRegion("button_stand");
        splitTR = assetStore.components.findRegion("button_split");
        doubleTR = assetStore.components.findRegion("button_double");
        insureTR = assetStore.components.findRegion("button_insure");
        strategyTR = assetStore.components.findRegion("button_strategy");
        pushTR = assetStore.components.findRegion("button_push");
        restartTR = assetStore.components.findRegion("button_restart");
        pushTR = assetStore.components.findRegion("button_push");
        settingTR = assetStore.components.findRegion("button_setting");
        quitTR = assetStore.components.findRegion("button_quit");
        surrenderTR = assetStore.components.findRegion("button_surrender");
        dealTR = assetStore.components.findRegion("button_deal");
        resumeTR = assetStore.components.findRegion("button_resume");

        //chip regions
        chip_50TR = assetStore.components.findRegion("chip_50");
        chip_100TR = assetStore.components.findRegion("chip_100");
        chip_250TR = assetStore.components.findRegion("chip_250");
        chip_500TR = assetStore.components.findRegion("chip_500");
        chip_1000TR = assetStore.components.findRegion("chip_1000");

        //game completion
        winDialogTR = assetStore.components.findRegion("dialog_win");
        looseDialogTR = assetStore.components.findRegion("dialog_loose");
        scoresTR = assetStore.components.findRegion("scores");
        starTR = assetStore.components.findRegion("star");
        deckTR = assetStore.components.findRegion("deck");
        menuTR = assetStore.components.findRegion("menu");
        counterBackgroundTR = assetStore.components.findRegion("counter_background");
        backgroundTR = assetStore.components.findRegion("background");

        //Other components
        chipsTR = assetStore.otherComponents.findRegion("chips");
        exitTR = assetStore.otherComponents.findRegion("exit");
        menusTR = assetStore.otherComponents.findRegion("menu");
        musicOffTR = assetStore.otherComponents.findRegion("music_off");
        musicOnTR = assetStore.otherComponents.findRegion("music_on");
        rewardButtonTR = assetStore.otherComponents.findRegion("restart");
        rewardButtonTR = assetStore.otherComponents.findRegion("reward_button");
        shareTR = assetStore.otherComponents.findRegion("share");
        soundOffTR = assetStore.otherComponents.findRegion("sound_off");
        soundOnTR = assetStore.otherComponents.findRegion("sound_on");
        restartsTR = assetStore.otherComponents.findRegion("restart");
        newGame();
    }

    private void initializeChips() {
        float centerOffset = 132;

        cardChips = new ArrayList<>();

        cardChips.add(new CardChips(chip_50TR, 50, chipCenterX, chipCenterY)); // Center
        cardChips.add(new CardChips(chip_250TR, 250, chipCenterX - centerOffset, chipCenterY + centerOffset)); // Top left
        cardChips.add(new CardChips(chip_500TR, 500, chipCenterX + centerOffset, chipCenterY + centerOffset)); // Top right
        cardChips.add(new CardChips(chip_1000TR, 1000, chipCenterX - centerOffset, chipCenterY - centerOffset)); // Bottom left
        cardChips.add(new CardChips(chip_100TR, 100, chipCenterX + centerOffset, chipCenterY - centerOffset)); // Bottom right

        for (CardChips chip : cardChips) {
            chip.setSize(170, 170);
            chip.setOrigin(0.5f, 0.5f); // Set origin to center for scaling
            chip.setPosition(chipCenterX, chipCenterY);
            chip.setScale(0.0f); // Start with chips hidden
            stage.addActor(chip);

            // Add pulsating animation to indicate interactivity
            chip.addAction(
                Actions.forever( // Loop the animation indefinitely
                    Actions.sequence(
                        Actions.scaleTo(1.1f, 1.1f, 0.5f), // Scale up
                        Actions.scaleTo(1.0f, 1.0f, 0.5f) // Scale down
                    )
                )
            );

            // Add listener for chip interaction
            chip.addListener(new ClickListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    chip.clearActions(); // Stop the pulsating animation for the selected chip
                    chip.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f)); // Immediate scale up on touch
                    return true;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    super.touchUp(event, x, y, pointer, button);
                    if (gamePoint.getPoints() < chip.amount) {
                        commonUtilInterface.showToast("Not enough chips for this bet");
                        return;
                    }


                    // Stop pulsating animations for all chips
                    for (CardChips c : cardChips) {
                        c.clearActions(); // Clear all actions (stop pulsating)
                    }
                    if (Settings.getSoundSetting()) {
                        assetStore.betchipSound.play();
                    }
                    betPlaced = true;
                    moveDealButton(dealImage, stage);
                    chip.setTouchable(Touchable.disabled);

                    // Move the selected chip to the center
                    chip.addAction(Actions.parallel(
                        Actions.scaleTo(1.2f, 1.2f, 0.5f),
                        Actions.moveTo(chipCenterX, chipCenterY, 0.5f)
                    ));

                    // Hide other chips immediately
                    for (CardChips c : cardChips) {
                        if (c.amount != chip.amount) {
                            c.clearActions(); // Stop any ongoing animations
                            c.addAction(Actions.scaleTo(0.0f, 0.0f, 0.2f)); // Scale down quickly
                            c.setTouchable(Touchable.disabled); // Disable interaction
                        }
                    }

                    betAmount = chip.amount;
                    lastSelectedChip = chip;

                    gamePoint.subtractPoints(betAmount);
                    pointsLabel.setText(GamePoint.POINTS + gamePoint.getPoints());
                    pointsLabel.layout();
                }
            });

            // Initial animation to show chips
            chip.addAction(Actions.parallel(
                Actions.delay(5.0f),
                Actions.moveTo(chip.originalX, chip.originalY, 0.5f),
                Actions.scaleTo(1.0f, 1.0f, 0.5f)
            ));
        }
    }


    private void loadPoints() {
        labelStyle = new Label.LabelStyle(assetStore.font, Color.WHITE);
        currentPoint = gamePoint.getPoints();
        // Create a label to display points
        pointsLabel = new Label(GamePoint.POINTS + currentPoint, labelStyle);
        // Force layout update
        pointsLabel.layout();

        // Add the label to the stage
        scoresImage.setSize(scoresImage.getWidth() + 200, scoresImage.getHeight() + 100);
        scoresImage.setPosition(50, 10);
        pointsLabel.setPosition(200, 85);
        stage.addActor(scoresImage);
        stage.addActor(pointsLabel);
    }

    /**
     * Deal implementation
     */
    public void dealImplementation() {
        dealImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                dealImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                dealImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                // Deal initial cards to player and dealer
                for (int i = 0; i < 2; i++) {
                    Card random = deck.getRandomCard();
                    playerHand.hit(random); // Deal a card to the player
                }

//                Card card = deck.getCard(String.valueOf(TEN_FLOWER))
//                    .orElseThrow(() -> new NoSuchElementException("Card not found: " + ACE_SPADE));
//
//                playerHand.hit(card);
//
//                Card card2 = deck.getCard(String.valueOf(KING_DIAMOND))
//                    .orElseThrow(() -> new NoSuchElementException("Card not found: " + KING_DIAMOND));
//                playerHand.hit(card2);


//                Card card1 = deck.getCard(String.valueOf(SEVEN_HEART)).orElseThrow(() -> new NoSuchElementException("Card not found: " + SEVEN_HEART));
//                playerHand.hit(card1);

                // Deal only one card to the dealer
                Card dealerCard = deck.getRandomCard();
                dealerHand.hit(dealerCard); // Deal a card to the dealer

//                Card dealerCard1 = deck.getCard(String.valueOf(ACE_SPADE)).orElseThrow(() -> new NoSuchElementException("Card not found: " + ACE_SPADE));
//                dealerHand.hit(dealerCard1); // Deal a card to the dea
////
//                Card dealerCard2 = deck.getCard(String.valueOf(KING_HEART)).orElseThrow(() -> new NoSuchElementException("Card not found: " + ACE_SPADE));;
//                dealerHand.hit(dealerCard2); // Deal a card to the dea


                playerHandDealer = new CardDealer(assetStore, stage, hitImage.getHeight() + hitImage.getHeight() / 4);
                playerHandDealer.dealCards(playerHand.cards);

                dealerHandDealer = new CardDealer(assetStore, stage, stage.getHeight() - 195 - 40);
                dealerHandDealer.dealCards(dealerHand.cards);
                hitImage.setVisible(true);
                standImage.setVisible(true);
                if (dealerCard.isAce()) {
                    insureImage.setVisible(true);
                    layoutButtonsAtBottomRightHalf(stage, hitImage, standImage, insureImage);
                } else {
                    layoutButtonsAtBottomRightHalf(stage, hitImage, standImage);
                }
                dealImage.getColor().a = 0;
                dealImage.addAction(
                    Actions.sequence(
                        Actions.parallel(
                            Actions.moveTo(-100, -200, 0.5f),// Move the card to its final position (0.5 seconds animation)
                            Actions.fadeIn(0.7f)
                        )
                    )
                );

                counterBackgroundImagePlayer.setVisible(true);
                counterBackgroundImageDealer.setVisible(true);
                handCardsCounter(playerHand.cards, "player", handCounter);
                handCardsCounter(dealerHand.cards, "dealer", dealerCounter);


                //blackjack
                if (playerHand.getSum() == 21) {
                    isBlackJack = true;
                    showGameOver(true);
                    return;
                }

                if (playerHand.hasSameValuePairOf10(playerHand.cards)) {
                    hitImage.setVisible(true);
                    splitImage.setVisible(true);
                    doubleImage.setVisible(true);
                    if (dealerCard.isAce()) {
                        layoutButtonsAtBottomRightHalf(stage, hitImage, standImage, insureImage, doubleImage, splitImage);
                    } else {
                        layoutButtonsAtBottomRightHalf(stage, hitImage, standImage, doubleImage, splitImage);
                    }
                    return;
                }

                if (playerHand.getSum() >= 10) {
                    doubleImage.setVisible(true);
                    if (dealerCard.isAce()) {
                        layoutButtonsAtBottomRightHalf(stage, hitImage, standImage, insureImage, doubleImage);
                    } else {
                        layoutButtonsAtBottomRightHalf(stage, hitImage, standImage, doubleImage);
                    }
                }
            }
        });
    }

    /**
     * Double chip implementation
     */
    private void doubleImplementation() {
        doubleImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                doubleImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (!betPlaced || lastSelectedChip == null) return;

                // Check if player has enough points for the double bet
                if (gamePoint.getPoints() < lastSelectedChip.amount) {
                    commonUtilInterface.showToast("Not enough chips to double");
                    return;
                }

                doubleImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                if (Settings.getSoundSetting()) {
                    assetStore.hitSound.play();
                    assetStore.betchipSound.play(); // Play chip sound for the additional bet
                }
                // Subtract the additional bet amount
                gamePoint.subtractPoints(lastSelectedChip.amount);
                pointsLabel.setText(GamePoint.POINTS + gamePoint.getPoints());
                pointsLabel.layout();

                betAmount += lastSelectedChip.amount; // Double the bet amount
                doubleBet = true;

                TextureRegionDrawable drawable = (TextureRegionDrawable) lastSelectedChip.getDrawable();
                TextureRegion region = drawable.getRegion();

                CardChips doubleChip = new CardChips(region, lastSelectedChip.amount,
                    lastSelectedChip.originalX, lastSelectedChip.originalY);
                doubleChip.setSize(170, 170);
                doubleChip.setOrigin(0.5f, 0.5f);
                doubleChip.setPosition(chipCenterX, chipCenterY);
                doubleChip.setScale(0.0f);
                stage.addActor(doubleChip);

                // Calculate offset position (place to the right of original chip)
                float offsetX = 120; // Adjust this value for desired spacing
                float targetX = lastSelectedChip.getX() + offsetX;
                float targetY = lastSelectedChip.getY();


                // Animate the additional chip to position beside the original
                doubleChip.addAction(Actions.sequence(
                    Actions.parallel(
                        Actions.scaleTo(1.2f, 1.2f, 0.3f),
                        Actions.moveTo(chipCenterX, chipCenterY, 0.3f)
                    ),
                    Actions.parallel(
                        Actions.scaleTo(1.2f, 1.2f, 0.2f),
                        Actions.moveTo(targetX, targetY, 0.2f)
                    ),
                    Actions.delay(1.0f), // 1 second delay after animation completes
                    Actions.run(new Runnable() {
                        @Override
                        public void run() {
                            // This code will run after the delay
                            Card random = deck.getRandomCard();
                            doubleImage.setVisible(false);
                            hitImage.setVisible(false);
                            standImage.setVisible(false);
                            splitImage.setVisible(false);
                            insureImage.setVisible(false);

                            if (!standMainHand && playerHand.getSum() <= 21) {
                                playerHand.hit(random);

                                playerHandDealer.addNewCardToStage(random, playerHand.cards);

                                int playerSum = playerHand.getSum();

                                if (playerSum == 21) {
                                    hitImage.setVisible(false);
                                    standImplementation(standImage);
                                    startDealerCardDrawing();
                                    return;
                                }

                                if (playerSum > 21) {
                                    showGameOver(false);
                                    return;
                                }

                                hitImage.setVisible(false);
                                standMainHand = true;
                                startDealerCardDrawing();
                                handCardsCounter(playerHand.cards, "player", handCounter);
                                lastSelectedChip = null;
                            }
                        }
                    })
                ));
            }
        });
    }

    private void splitImplementation() {
        splitImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                splitImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                splitImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                hitImage.setVisible(true);
                splitImage.setVisible(false);
                doubleImage.setVisible(false);
                insureImage.setVisible(false);
                counterBackgroundImagePlayer.setVisible(false);
                handCounter.setVisible(false);
                layoutButtonsAtBottomRightHalf(stage, hitImage, standImage);
                split = true;
                for (Image image : playerHandDealer.cardImages) {
                    image.remove();
                }
                playerHandDealer.cardImages.clear();
                //split the cards
                playerHand.splitSameValueCardPair(playerHand.cards, splitHand.cards);

                playerHandDealer.dealSplitCards(playerHand.cards, playerHandDealer.centerOfCenterXPositionLeft(splitImage));
                playerHandDealer.dealSplitCards(splitHand.cards, playerHandDealer.centerOfCenterXPositionRight(splitImage));
                handCardsCounter(playerHand.cards, "splitHandOne", splitHandOneCounter);
                handCardsCounter(splitHand.cards, "splitHandTwo", splitHandTwoCounter);
                counterBackgroundImageSplitHandTwo.setVisible(true);
                counterBackgroundImageSplitHandOne.setVisible(true);
            }
        });
    }

    public void hitImplementation(Image hitImage) {
        hitImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                hitImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                hitImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                if (Settings.getSoundSetting()) {
                    assetStore.hitSound.play();
                }
                doubleImage.setVisible(false);
                splitImage.setVisible(false);
                insureImage.setVisible(false);
                // Draw a random card from the deck
                Card random = deck.getRandomCard();

//                Card random = deck.getCard(String.valueOf(ACE_SPADE))
//                    .orElseThrow(() -> new NoSuchElementException("Card not found: " + ACE_SPADE));

                // Handle non-split scenario
                if (!split) {
                    if (!standMainHand && playerHand.getSum() <= 21) { // Only allow hits if sum <= 21
                        playerHand.hit(random);
                        playerHandDealer.addNewCardToStage(random, playerHand.cards);
                        int playerSum = playerHand.getSum(); // Store updated sum after hitting
                        handCardsCounter(playerHand.cards, "player", handCounter);
                        if (playerSum == 21) {
                            hitImage.setVisible(false);
                            standMainHand = true;
                            startDealerCardDrawing();
                            doubleImage.setVisible(false);
                            standImage.setVisible(false);
                            return;
                        }

                        if (playerSum > 21) {
                            showGameOver(false);
                        }
                    }
                }
                // Handle split scenario
                else {
                    // First split hand logic
                    if (!standSplitHand1 && playerHand.getSum() <= 21) { // Only allow hits if sum <= 21
                        playerHand.hit(random);
                        playerHandDealer.addNewCardToSplitStage(random, playerHand.cards, "player", hitImage);
                        int playerSum = playerHand.getSum(); // Store updated sum after hitting
                        handCardsCounter(playerHand.cards, "splitHandOne", splitHandOneCounter);
                        if (playerSum == 21) {
                            standSplitHand1 = true;
                            return;
                        }

                        if (playerSum > 21) {
                            standSplitHand1 = true; // Prevent further hits on this hand
                        }
                    }
                    // Second hand logic
                    else if (!standSplitHand2 && splitHand.getSum() <= 21) { // Only allow hits if sum <= 21
                        splitHand.hit(random);
                        playerHandDealer.addNewCardToSplitStage(random, splitHand.cards, "split", hitImage);
                        int playerSum = splitHand.getSum(); // Store updated sum after hitting
                        handCardsCounter(splitHand.cards, "splitHandTwo", splitHandTwoCounter);
                        if (playerSum == 21) {
                            standSplitHand2 = true;
                            hitImage.setVisible(false);
                            return;
                        }

                        if (playerSum > 21) {
                            standSplitHand2 = true; // Prevent further hits on this hand
                            hitImage.setVisible(false);
                            standImage.setVisible(false);
                        }

                        if (playerHand.getSum() > 21 && splitHand.getSum() > 21) {
                            showGameOver(false);
                            return;
                        }

                        if (standSplitHand1 && splitHand.getSum() > 21) {
                            startDealerCardDrawing();
                        }
                    } else if (!standSplitHand2 && splitHand.getSum() > 21) {
                        hitImage.setVisible(false);
                        standMainHand = true;
                        startDealerCardDrawing();
                        doubleImage.setVisible(false);
                        standImage.setVisible(false);
                    }
                }
            }
        });
    }

    public void standImplementation(Image standImage) {
        standImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                standImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                standImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                if (Settings.getSoundSetting()) {
                    assetStore.bell.play();
                }
                doubleImage.setVisible(false);
                splitImage.setVisible(false);
                insureImage.setVisible(false);
                // Set the appropriate stand flag based on the current hand
                if (!split) {
                    standMainHand = true; // Stand for the main hand in non-split scenario
                } else {
                    if (!standSplitHand1) {
                        standSplitHand1 = true; // Stand for the first split hand
                    } else if (!standSplitHand2) {
                        standSplitHand2 = true; // Stand for the second split hand
                    }
                }

                // Hide hit and stand buttons if all hands are stood on
                if ((!split && standMainHand) || (split && standSplitHand1 && standSplitHand2)) {
                    hitImage.setVisible(false);
                    standImage.setVisible(false);
                }

                // Start the dealer's card-drawing process only if all stands are catered for
                if ((!split && standMainHand) || (split && standSplitHand1 && standSplitHand2) || (split && standSplitHand1 && splitHand.getSum() > 21)) {
                    startDealerCardDrawing();
                }
            }
        });
    }

    public void startDealerCardDrawing() {
        stage.addAction(Actions.sequence(
            Actions.delay(0.5f), // Initial delay before starting
            Actions.run(new Runnable() {
                @Override
                public void run() {
                    if (dealerHand.getSum() == 21 && playerHand.getSum() < dealerHand.getSum()) {
                        showGameOver(false);//Dealer wins
                        return;
                    }

                    if (dealerHand.getSum() == 21 && playerHand.getSum() == 21) {
                        pushImage.setVisible(true);
                        standImage.setVisible(false);
                        hitImage.setVisible(false);
                        layoutButtonsAtBottomRightHalf(stage, pushImage);
                        return;
                    }

                    if (dealerHand.getSum() >= 17) {
                        // Check if the dealer has busted
                        if (dealerHand.getSum() > 21) {
                            showGameOver(true); // Dealer busts, player wins
                        } else {
                            // Check if the dealer's hand is greater than both player hands (if split exists)
                            boolean dealerWinsMain = dealerHand.getSum() > playerHand.getSum();
                            boolean dealerWinsSplit = !split || dealerHand.getSum() > splitHand.getSum();

                            if (dealerWinsMain && dealerWinsSplit) {
                                showGameOver(false); // Dealer wins against both hands
                            } else if (!dealerWinsMain && !dealerWinsSplit) {
                                showGameOver(true); // Player wins with both hands
                            } else {
                                // If the dealer wins against one hand but not the other, handle accordingly
                                showGameOver(true);
                            }
                        }
                    } else {
                        drawDealerCards(); // Dealer must keep drawing until their hand is >= 17
                    }
                }
            })
        ));
    }

    public void pushImplementation() {
        pushImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                pushImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                pushImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));

                // Move the cards back to the deck visually
                playerHandDealer.moveCardsBackToDeck();
                dealerHandDealer.moveCardsBackToDeck();

                counterBackgroundImagePlayer.setVisible(false);
                counterBackgroundImageDealer.setVisible(false);
                dealerCounter.setVisible(false);
                handCounter.setVisible(false);


                // Clear only the hands (remove cards from logic and screen)
                playerHand.clear();
                dealerHand.clear();
                splitHand.clear(); // if you're using split hands in the game

                // Reset control flags for the next round
                standSplitHand1 = false;
                standSplitHand2 = false;
                standMainHand = false;
                split = false;

                // Don't change betPlaced or gamePoint, keep the existing bet
                // You are NOT refunding the bet, just moving to the next round

                // Show deal button for a new deal
                moveDealButton(dealImage, stage);

                // Hide buttons that shouldn't be active yet
                pushImage.setVisible(false);
                standImage.setVisible(false);
                hitImage.setVisible(false);
                splitImage.setVisible(false);
                doubleImage.setVisible(false);
                insureImage.setVisible(false);
                counterBackgroundImageSplitHandOne.setVisible(false);
                counterBackgroundImageSplitHandTwo.setVisible(false);

                // Reset hand counters if you need to
                initializeHandCounter();
                initializeDealerCounter();
                initializeSplitHandOneCounter();
                initializeSplitHandTwoCounter();

                // Ready for the next deal!
                //Reset split hand if it's used
                splitHand.clear();
                standSplitHand1 = false;
                standSplitHand2 = false;
                split = false;
            }
        });
    }

    public void insuranceImplementation() {
        insureImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                insureImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                insureImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                insureImage.setVisible(false);
                insure = true;

                gamePoint.subtractPoints(betAmount / 2);
                pointsLabel.setText(GamePoint.POINTS + gamePoint.getPoints());
                pointsLabel.layout();
            }
        });
    }

    public void drawDealerCards() {
        int dealerHandSum = dealerHand.getSum();
        int playerHandSum = playerHand.getSum();

        // First, check for an immediate push
        if (dealerHandSum == playerHandSum) {
            pushImage.setVisible(true);
            standImage.setVisible(false);
            hitImage.setVisible(false);
            layoutButtonsAtBottomRightHalf(stage, pushImage);
            return;  // Stop further execution
        }

        // Dealer must draw if their sum is less than 17 or lower than player's sum
        if (dealerHandSum < 17 || dealerHandSum < playerHandSum) {
            Card random = deck.getRandomCard();

            dealerHand.hit(random);
            dealerHandDealer.addNewCardToStage(random, dealerHand.cards);

            handCardsCounter(dealerHand.cards, "dealer", dealerCounter);

            stage.addAction(Actions.sequence(
                Actions.delay(0.5f), // Delay for 0.5 seconds
                Actions.run(() -> {
                    if (Settings.getSoundSetting()) {
                        assetStore.hitSound.play();
                    }
                    int updatedDealerHandSum = dealerHand.getSum();

                    // Re-check push condition after dealer draws
                    if (updatedDealerHandSum == playerHandSum) {
                        pushImage.setVisible(true);
                        standImage.setVisible(false);
                        hitImage.setVisible(false);
                        layoutButtonsAtBottomRightHalf(stage, pushImage);
                        return;
                    }

                    // Dealer busts (player wins)
                    if (updatedDealerHandSum > 21) {
                        showGameOver(true);
                        return;
                    }

                    // Dealer stops drawing if >= 17
                    if (updatedDealerHandSum >= 17) {
                        if (!split) {
                            // Standard game: Check for dealer win or player win
                            if (updatedDealerHandSum > playerHandSum) {
                                showGameOver(false); // Dealer wins
                            } else {
                                showGameOver(true); // Player wins
                            }
                        } else {
                            // Split scenario: Check both hands
                            boolean dealerBeatsPlayerHand = updatedDealerHandSum > playerHandSum;
                            boolean dealerBeatsSplitHand = updatedDealerHandSum > splitHand.getSum();

                            if (dealerBeatsPlayerHand && dealerBeatsSplitHand) {
                                showGameOver(false); // Dealer wins both hands
                            } else if (!dealerBeatsPlayerHand && !dealerBeatsSplitHand) {
                                showGameOver(true); // Player wins both hands
                            } else {
                                // Dealer wins one hand, player wins the other
                                showGameOver(true);
                                //showPartialWin(); // Implement this function
                            }
                        }
                        return;
                    }

                    // If dealer sum is still < 17, continue drawing
                    drawDealerCards();
                })
            ));
        } else {
            // If dealer doesn't need to draw, decide winner
            if (dealerHandSum > playerHandSum) {
                showGameOver(false); // Dealer wins
            } else {
                showGameOver(true); // Player wins
            }
        }
    }

    private float deckYPosition() {
        return this.stage.getHeight() / 2 - (float) 195 / 2; //195 is the deck height
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta); // Update the stage
        stage.draw(); // Draw the stage
    }

    @Override
    public boolean onBack() {
        // First back closes an open dialog, the next one leaves the table
        if (overlayShowing) {
            CommonGameFunctions.hideOverlayScreen();
            overlayShowing = false;
            return true;
        }
        return false;
    }

    public void showGameOver(boolean win) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage);
        looseDialogImage.setSize(636, 636);
        winDialogImage.setSize(636, 636);
        winDialogImage.setPosition(stage.getWidth() / 2 - winDialogImage.getWidth() / 2, stage.getHeight() / 2 - winDialogImage.getHeight() / 2);
        looseDialogImage.setPosition(stage.getWidth() / 2 - looseDialogImage.getWidth() / 2, stage.getHeight() / 2 - looseDialogImage.getHeight() / 2);

        // When the player wins:
        if (win) {
            overlay.addActor(winDialogImage);
            GameText.addRibbonText(assetStore, overlay, winDialogImage, "WIN");

            int pointsAwarded;

            // The bet was taken when it was placed, so a win returns it plus the winnings
            if (isBlackJack) {
                pointsAwarded = betAmount + (int) (betAmount * 1.5); // blackjack pays 3:2
                if (Settings.getSoundSetting()) {
                    assetStore.blackJack.play();
                }
            } else {
                pointsAwarded = betAmount * 2; // even money
                if (Settings.getSoundSetting()) {
                    assetStore.win.play();
                }
            }

            gamePoint.addPoints(pointsAwarded);
            gamePoint.newScore(pointsLabel, pointsAwarded);

        } else {
            overlay.addActor(looseDialogImage);
            GameText.addRibbonText(assetStore, overlay, looseDialogImage, "LOSE");
        }

        // Chips live on the server; send it this round's result
        if (!roundReported && betAmount > 0) {
            roundReported = true;
            PlayerApi.reportClassicRound(betAmount, gamePoint.getPoints() - currentPoint);
        }

        // Make sure the label reflects the updated points on overlay
        pointsLabel.setText(GamePoint.POINTS + gamePoint.getPoints());
        pointsLabel.layout();


        restartImage.setSize(350, 150);
        restartImage.setPosition(stage.getWidth() / 2 - restartImage.getWidth() / 2, restartImage.getHeight() / 2);
        overlay.addActor(restartImage);

        labelStyle = new Label.LabelStyle(assetStore.dialogFont, Color.valueOf("#EB2F23"));
        blackJackLabelStyle = new Label.LabelStyle(assetStore.blackJackFont, Color.valueOf("#F4AD31"));
        dialogFontLabelStyle = new Label.LabelStyle(assetStore.dialogCounterFont, Color.valueOf("#FFFFFF"));
        betAmountLabelStyle = new Label.LabelStyle(assetStore.betAmountDialogFont, Color.valueOf("#8C0303"));

        // Create a label to display points
        pointsLabel = new Label(GamePoint.POINTS + gamePoint.getPoints(), labelStyle);
        blackJackLabel = new Label("BLACKJACK", blackJackLabelStyle);
        dealerDialogLabel = new Label("Dealer", dialogFontLabelStyle);
        playerDialogLabel = new Label("Player", dialogFontLabelStyle);
        playerDialogCount = new Label(String.valueOf(playerHand.getSum()), dialogFontLabelStyle);
        dealerDialogCount = new Label(String.valueOf(dealerHand.getSum()), dialogFontLabelStyle);

        betAmountLabel = new Label(String.valueOf(betAmount), betAmountLabelStyle);
        blackJackLabel.setPosition(overlay.getWidth() / 2 - blackJackLabel.getWidth() / 2, (float) (overlay.getHeight() - (blackJackLabel.getHeight() / 2 * 3)));
        // The chips bar sits a quarter of the way up the dialog art; centre on it whatever the font height
        float chipsBarY = winDialogImage.getY() + winDialogImage.getHeight() * 0.25f;
        pointsLabel.pack();
        betAmountLabel.pack();
        pointsLabel.setPosition(overlay.getWidth() / 2 - winDialogImage.getWidth() / 2 + 200, chipsBarY - pointsLabel.getHeight() / 2);
        betAmountLabel.setPosition(overlay.getWidth() / 2 - winDialogImage.getWidth() / 2 + 124 - betAmountLabel.getWidth() / 2, chipsBarY - betAmountLabel.getHeight() / 2);

        dealerDialogLabel.setPosition(stage.getWidth() / 2 - winDialogImage.getWidth() / 2 + 80, winDialogImage.getHeight() / 2 + 250);
        playerDialogLabel.setPosition(stage.getWidth() * 3 / 5 + 50 - winDialogImage.getWidth() / 4, winDialogImage.getHeight() / 2 + 250);

        dealerDialogCount.setPosition(stage.getWidth() / 2 - winDialogImage.getWidth() / 2 + 120, winDialogImage.getHeight() / 2 + 210);
        playerDialogCount.setPosition(stage.getWidth() * 3 / 5 + 80 - winDialogImage.getWidth() / 4, winDialogImage.getHeight() / 2 + 210);

        // Force layout update
        pointsLabel.layout();

        // Add the label to the stage
        overlay.addActor(pointsLabel);
        overlay.addActor(dealerDialogLabel);
        overlay.addActor(playerDialogLabel);
        overlay.addActor(dealerDialogCount);
        overlay.addActor(playerDialogCount);
        overlay.addActor(betAmountLabel);
        if (isBlackJack) {
            blackJackEffect(stage);
            isBlackJack = false;
            overlay.addActor(blackJackLabel);
            if (Settings.getSoundSetting()) {
                assetStore.blackJack.play();
            }
        }

        playerHandDealer = new CardDealer(assetStore, overlay.getStage(), hitImage.getHeight() + hitImage.getHeight() / 4);
        playerHandDealer.dealGameOverCards(overlay.getStage(), playerHand.cards, playerHandDealer.centerOfCenterXPositionRight(hitImage), stage.getHeight() / 2);

        dealerHandDealer = new CardDealer(assetStore, overlay.getStage(), hitImage.getHeight() + hitImage.getHeight() / 4);
        dealerHandDealer.dealGameOverCards(overlay.getStage(), dealerHand.cards, playerHandDealer.centerOfCenterXPositionLeft(hitImage) - 200, stage.getHeight() / 2);

        restartImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                restartImage.addAction(Actions.scaleTo(1.2f, 1.2f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                restartImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                if (Settings.getSoundSetting()) {
                    assetStore.betChipInitializer.play();
                }
                CommonGameFunctions.hideOverlayScreen();

                // Move the cards back to the deck visually
                playerHandDealer.moveCardsBackToDeck();
                dealerHandDealer.moveCardsBackToDeck();

                // Clear the hands and the lists of cards on stage
                playerHand.clear();
                dealerHand.clear();

                newGame();
            }
        });

        CommonGameFunctions.showOverlay();
        overlayShowing = true;
        betPlaced = false;
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

    public void newGame() {
        stage.clear();
        deck = new Deck();
        playerHand = new PlayerHand();
        dealerHand = new DealerHand();
        splitHand = new SplitHand();
        gamePoint = new GamePoint();
        authenticateUser();


        backgroundImage = new Image(backgroundTR);
        backgroundImage.setSize(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        stage.addActor(backgroundImage);

        deckImage = new Image(deckTR);
        deckImage.setPosition(20, deckYPosition());
        stage.addActor(deckImage);

        dealImage = new Image(dealTR);
        dealImage.setPosition(stage.getWidth() / 2 - dealImage.getWidth() / 2, -10);
        stage.addActor(dealImage);

        pushImage = new Image(pushTR);

        hitImage = new Image(hitTR);
        standImage = new Image(standTR);
        splitImage = new Image(splitTR);
        doubleImage = new Image(doubleTR);
        insureImage = new Image(insureTR);
        settingImage = new Image(settingTR);
        menuImage = new Image(menuTR);
        startImage = new Image(starTR);
        quitImage = new Image(quitTR);
        strategyImage = new Image(strategyTR);
        restartImage = new Image(restartTR);
        scoresImage = new Image(scoresTR);
        resumeImage = new Image(resumeTR);
        surrenderImage = new Image(surrenderTR);
        quitImage = new Image(quitTR);

        chipsImage = new Image(chipsTR);
        exitImage = new Image(exitTR);
        menusImage = new Image(menusTR);
        musicOffImage = new Image(musicOffTR);
        musicOnImage = new Image(musicOnTR);
        restartsImage = new Image(restartsTR);
        rewardButtonImage = new Image(rewardButtonTR);
        shareImage = new Image(shareTR);
        soundsOffImage = new Image(soundOffTR);
        soundOnImage = new Image(soundOnTR);

        counterBackgroundImagePlayer = new Image(counterBackgroundTR);
        counterBackgroundImageDealer = new Image(counterBackgroundTR);
        counterBackgroundImageSplitHandOne = new Image(counterBackgroundTR);
        counterBackgroundImageSplitHandTwo = new Image(counterBackgroundTR);
        winDialogImage = new Image(winDialogTR);
        looseDialogImage = new Image(looseDialogTR);

        counterBackgroundImagePlayer.setSize(counterBackgroundImageDealer.getWidth() * 4, counterBackgroundImageDealer.getHeight() * 4);
        counterBackgroundImagePlayer.setPosition(stage.getWidth() / 2 - counterBackgroundImagePlayer.getWidth() / 2, hitImage.getHeight() + hitImage.getHeight() / 4);

        counterBackgroundImageSplitHandOne.setSize(counterBackgroundImageDealer.getWidth() * 4, counterBackgroundImageDealer.getHeight() * 4);
        counterBackgroundImageSplitHandOne.setPosition(stage.getWidth() / 2 - counterBackgroundImagePlayer.getWidth() / 2, hitImage.getHeight() + hitImage.getHeight() / 4);
        counterBackgroundImageSplitHandOne.setPosition((stage.getWidth() / 4) - (counterBackgroundImageSplitHandTwo.getWidth() / 2) / 4, hitImage.getHeight() + hitImage.getHeight() / 4);

        counterBackgroundImageSplitHandTwo.setSize(counterBackgroundImageDealer.getWidth() * 4, counterBackgroundImageDealer.getHeight() * 4);
        counterBackgroundImageSplitHandTwo.setPosition((stage.getWidth() * 3 / 4 - 50), hitImage.getHeight() + hitImage.getHeight() / 4);

        counterBackgroundImageDealer.setSize(counterBackgroundImageDealer.getWidth() * 4, counterBackgroundImageDealer.getHeight() * 4);
        counterBackgroundImageDealer.setPosition(stage.getWidth() / 2 - counterBackgroundImageDealer.getWidth() / 2, stage.getHeight() - 320);

        dealerHandDealer = new CardDealer(assetStore, stage, stage.getHeight() - dealImage.getHeight() - 40);

        menusImage.setSize(menusImage.getWidth() * 3, menusImage.getHeight() * 3);
        float padding = 50f; // Optional: add some space from the edges
        float x = stage.getWidth() - menusImage.getWidth() - padding;
        float y = stage.getHeight() - menusImage.getHeight() - padding;
        menusImage.setPosition(x, y);

        stage.addActor(standImage);
        stage.addActor(hitImage);
        stage.addActor(splitImage);
        stage.addActor(doubleImage);
        stage.addActor(pushImage);
        stage.addActor(insureImage);
        stage.addActor(surrenderImage);
        stage.addActor(menusImage);
        stage.addActor(counterBackgroundImagePlayer);
        stage.addActor(counterBackgroundImageDealer);
        stage.addActor(counterBackgroundImageSplitHandTwo);
        stage.addActor(counterBackgroundImageSplitHandOne);

        hitImage.setVisible(false);
        surrenderImage.setVisible(false);
        standImage.setVisible(false);
        splitImage.setVisible(false);
        doubleImage.setVisible(false);
        pushImage.setVisible(false);
        insureImage.setVisible(false);
        counterBackgroundImageDealer.setVisible(false);
        counterBackgroundImagePlayer.setVisible(false);
        counterBackgroundImageSplitHandOne.setVisible(false);
        counterBackgroundImageSplitHandTwo.setVisible(false);
        dealImage.setVisible(betPlaced);

        buttonSize(standImage, hitImage, splitImage, doubleImage, pushImage, insureImage);

        dealImplementation();
        hitImplementation(hitImage);
        standImplementation(standImage);
        pushImplementation();
        splitImplementation();
        doubleImplementation();
        insuranceImplementation();

        standSplitHand1 = false; //stand on first split hand
        standSplitHand2 = false; //stand on second split hand
        standMainHand = false;
        doubleBet = false;
        split = false;
        lastSelectedChip = null;

        initializeChips();
        loadPoints();
        initializeHandCounter();
        initializeDealerCounter();
        initializeSplitHandOneCounter();
        initializeSplitHandTwoCounter();
        currentPoint = gamePoint.getPoints();
        roundReported = false;
        sendGameRequest();
        showMenuItemDialog(stage, commonUtilInterface, menusImage, menuImage, musicOffImage, musicOnImage, soundOnImage, soundsOffImage, shareImage, exitImage, rewardButtonImage, overlayShowing);
    }

    private void initializeHandCounter() {
        labelStyle = new Label.LabelStyle(assetStore.counterFont, Color.WHITE);

        handCounter = new Label("0", labelStyle);
        handCounter.setPosition(stage.getWidth() / 2 - counterBackgroundImagePlayer.getWidth() / 4, 268f - 80f);

        handCounter.setVisible(false);
        stage.addActor(handCounter);
    }

    private void initializeSplitHandOneCounter() {
        labelStyle = new Label.LabelStyle(assetStore.counterFont, Color.WHITE);

        splitHandOneCounter = new Label("0", labelStyle);
        splitHandOneCounter.setPosition((stage.getWidth() / 4 + 20), 268f - 80f);

        splitHandOneCounter.setVisible(false);
        stage.addActor(splitHandOneCounter);
    }

    private void initializeSplitHandTwoCounter() {
        labelStyle = new Label.LabelStyle(assetStore.counterFont, Color.WHITE);

        splitHandTwoCounter = new Label("0", labelStyle);
        splitHandTwoCounter.setPosition((stage.getWidth() * 3 / 4 - 25), 268f - 80f);

        splitHandTwoCounter.setVisible(false);
        stage.addActor(splitHandTwoCounter);
    }

    private void initializeDealerCounter() {
        labelStyle = new Label.LabelStyle(assetStore.counterFont, Color.WHITE);

        dealerCounter = new Label("0", labelStyle);
        dealerCounter.setPosition(stage.getWidth() / 2 - counterBackgroundImageDealer.getWidth() / 4, stage.getHeight() - 305);

        dealerCounter.setVisible(false);
        stage.addActor(dealerCounter);
    }

    private void sendGameRequest() {
        deckImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                deckImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                deckImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                initiateGameRequest();

                OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage);

                float buttonWidth = 370f, buttonHeight = 120f;

                Table table = new Table();
                table.setFillParent(true);
                table.center();

                opponentLabelStyle = new Label.LabelStyle(assetStore.dialogFont, Color.valueOf("#EB2F23"));
                timeCountLabelStyle = new Label.LabelStyle(assetStore.dialogFont, Color.valueOf("#EB2F23"));

                opponentNameLabel = new Label("Waiting for opponent to accept request", opponentLabelStyle);
                timeCountLabel = new Label("5", timeCountLabelStyle);

                // Arrange items vertically
                table.add(opponentNameLabel).padBottom(20f).row();
                table.add(timeCountLabel).padBottom(20f).row();
                table.add(resumeImage).size(buttonWidth, buttonHeight).padBottom(20f).row();

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

                resumeImage.addListener(new ClickListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        resumeImage.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                        return true;
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                        rewardButtonImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                        if (overlayShowing) {
                            overlayShowing = false;
                            newGame();
                        }
                    }
                });// Start after 0 seconds, repeat every 1 second, for 5 times
            }
        });
    }

    private void setupAcceptClickListener() {
        scoresImage.clearListeners(); // prevent multiple listeners stacking
        scoresImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                scoresImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                acceptInitiatedGame();
            }
        });
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

    private void initiateGameRequest() {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "request_game");
        data.put("opponent_id", 1);

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

        if (webSocketHandler.isConnected()) {
            webSocketHandler.sendMessage(jsonString);
        }
    }
}
