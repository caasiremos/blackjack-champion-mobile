package com.piga21.rules;


import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import com.piga21.AssetStore;
import com.piga21.cards.Card;
import com.piga21.cards.CardDealer;
import com.piga21.cards.Deck;
import com.piga21.hands.PlayerHand;
import com.piga21.utils.Settings;

public class HitRule {
    private AssetStore assetStore;
    private Deck deck;
    private PlayerHand playerHand;
    public CardDealer playerHandDealer, dealerHandDealer;
    private Image doubleImage;
    private Image standImage;
    private Image splitImage;
    private Image insureImage;
    private Boolean standMainHand = false;
    private Boolean split = false;
    private Boolean insure = false;
    public Image hitImage;

    public void hitRule(Image hitImage, Image doubleImage, Image standImage, Image splitImage, Image insureImage, AssetStore assetStore, Deck deck, PlayerHand playerHand) {
        this.assetStore = assetStore;
        this.deck = deck;
        this.playerHand = playerHand;
        this.doubleImage = doubleImage;
        this.standImage = standImage;
        this.splitImage = splitImage;
        this.insureImage = insureImage;
        this.hitImage = hitImage;

    }

//    public void hitImplementation(Image hitImage) {
//        hitImage.addListener(new ClickListener() {
//            @Override
//            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
//                hitImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
//                return true;
//            }
//
//            @Override
//            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
//                hitImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
//                if (Settings.getSoundSetting()) {
//                    assetStore.hitSound.play();
//                }
//                doubleImage.setVisible(false);
//                splitImage.setVisible(false);
//                insureImage.setVisible(false);
//                // Draw a random card from the deck
//                Card random = deck.getRandomCard();
//
////                Card random = deck.getCard(String.valueOf(ACE_SPADE))
////                    .orElseThrow(() -> new NoSuchElementException("Card not found: " + ACE_SPADE));
//
//                // Handle non-split scenario
//                if (!split) {
//                    if (!standMainHand && playerHand.getSum() <= 21) { // Only allow hits if sum <= 21
//                        playerHand.hit(random);
//                        playerHandDealer.addNewCardToStage(random, playerHand.cards);
//                        int playerSum = playerHand.getSum(); // Store updated sum after hitting
//                        handCardsCounter(playerHand.cards, "player");
//                        if (playerSum == 21) {
//                            hitImage.setVisible(false);
//                            standMainHand = true;
//                            startDealerCardDrawing();
//                            doubleImage.setVisible(false);
//                            standImage.setVisible(false);
//                            return;
//                        }
//
//                        if (playerSum > 21) {
//                            showGameOver(false);
//                        }
//                    }
//                }
//                // Handle split scenario
//                else {
//                    // First split hand logic
//                    if (!standSplitHand1 && playerHand.getSum() <= 21) { // Only allow hits if sum <= 21
//                        playerHand.hit(random);
//                        playerHandDealer.addNewCardToSplitStage(random, playerHand.cards, "player", hitImage);
//                        int playerSum = playerHand.getSum(); // Store updated sum after hitting
//                        handCardsCounter(playerHand.cards, "splitHandOne");
//                        if (playerSum == 21) {
//                            standSplitHand1 = true;
//                            return;
//                        }
//
//                        if (playerSum > 21) {
//                            standSplitHand1 = true; // Prevent further hits on this hand
//                        }
//                    }
//                    // Second hand logic
//                    else if (!standSplitHand2 && splitHand.getSum() <= 21) { // Only allow hits if sum <= 21
//                        splitHand.hit(random);
//                        playerHandDealer.addNewCardToSplitStage(random, splitHand.cards, "split", hitImage);
//                        int playerSum = splitHand.getSum(); // Store updated sum after hitting
//                        handCardsCounter(splitHand.cards, "splitHandTwo");
//                        if (playerSum == 21) {
//                            standSplitHand2 = true;
//                            hitImage.setVisible(false);
//                            return;
//                        }
//
//                        if (playerSum > 21) {
//                            standSplitHand2 = true; // Prevent further hits on this hand
//                            hitImage.setVisible(false);
//                            standImage.setVisible(false);
//                        }
//
//                        if (playerHand.getSum() > 21 && splitHand.getSum() > 21) {
//                            showGameOver(false);
//                            return;
//                        }
//
//                        if (standSplitHand1 && splitHand.getSum() > 21) {
//                            startDealerCardDrawing();
//                        }
//                    } else if (!standSplitHand2 && splitHand.getSum() > 21) {
//                        hitImage.setVisible(false);
//                        standMainHand = true;
//                        startDealerCardDrawing();
//                        doubleImage.setVisible(false);
//                        standImage.setVisible(false);
//                    }
//                }
//            }
//        });
//    }

}
