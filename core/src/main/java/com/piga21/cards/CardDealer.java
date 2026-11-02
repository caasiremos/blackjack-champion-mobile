package com.piga21.cards;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

import java.util.ArrayList;
import java.util.List;

import com.piga21.AssetStore;

public class CardDealer {
    public List<Image> cardImages;
    AssetStore assetStore;
    Stage stage;
    float y;
    final static float spacing = 10;

    public CardDealer(AssetStore assetStore, Stage stage, float y) {
        this.assetStore = assetStore;
        this.stage = stage;
        this.y = y;
        cardImages = new ArrayList<>();
    }

    public float calculateTotalCardWidth(List<Card> cards) {
        float totalWidth = 0;
        for (Card card : cards) {
            if (card == null) {
                continue; // Skip null cards
            }
            TextureRegion texture = assetStore.getTextureRegion(card);
            if (texture == null) {
                System.err.println("Warning: No texture found for card: " + card);
                continue; // Skip null textures
            }
            totalWidth += assetStore.getTextureRegion(card).getRegionWidth() + spacing;
        }
        totalWidth -= spacing; // Remove the last spacing
        return totalWidth;
    }

    private float deckYPosition() {
        return this.stage.getHeight() / 2 - (float) 195 / 2;
    }

    public void addNewCardToStage(Card card, List<Card> cards) {
        // Calculate the total width of all cards including spacing
        float totalWidth = calculateTotalCardWidth(cards);

        // Calculate the starting x position to center the cards
        float screenWidth = stage.getWidth(); // Get the width of the stage
        float startX = (screenWidth - totalWidth) / 2; // Center the cards horizontally

        // Calculate the x position for the new card
        float cardX = startX + (cards.size() - 1) * (assetStore.getTextureRegion(card).getRegionWidth() + spacing);

        // Create the card image
        Image cardImage = new Image(assetStore.getTextureRegion(card));
        cardImage.setPosition(20, deckYPosition()); // Start off-screen to the left

        // Set the initial opacity of the card to 0 (fully transparent)
        cardImage.getColor().a = 0;

        // Add the card to the stage
        stage.addActor(cardImage);
        cardImages.add(cardImage);

        // Create an action sequence for the card
        cardImage.addAction(
            Actions.sequence(
                Actions.parallel(
                    Actions.moveTo(cardX, y, 0.5f), // Move the card to its final position (0.5 seconds animation)
                    Actions.fadeIn(0.5f) // Fade in the card over 0.5 seconds
                )
            )
        );
    }

    public void dealCards(List<Card> cards) {
        // Define the delay between cards (in seconds)
        float delayBetweenCards = 0.2f; // 0.5 seconds delay

        // Calculate the total width of all cards including spacing
        float totalWidth = 0;
        for (Card card : cards) {
            if (card == null) {
                System.err.println("Warning: A TextureRegion is null. Skipping this card.");
                continue; // Skip null regions
            }
            totalWidth += assetStore.getTextureRegion(card).getRegionWidth() + spacing;
        }
        totalWidth -= spacing; // Remove the last spacing

        // Calculate the starting x position to center the cards
        float screenWidth = stage.getWidth(); // Get the width of the stage
        float startX = (screenWidth - totalWidth) / 2; // Center the cards horizontally

        // Iterate over the card regions
        for (int i = 0; i < cards.size(); i++) {
            final TextureRegion region = assetStore.getTextureRegion(cards.get(i));
            if (region == null) {
                System.err.println("Warning: TextureRegion for card index " + i + " is null. Skipping this card.");
                continue; // Skip null regions
            }

            final float cardX = startX; // Calculate the x position for this card

            // Create the card image
            Image cardImage = new Image(region);
            cardImage.setPosition(20, stage.getHeight() / 2 - (float) 195 / 2);
            stage.addActor(cardImage);
            cardImages.add(cardImage);

            // Create an action sequence for the card
            cardImage.getColor().a = 0;
            cardImage.addAction(
                Actions.sequence(
                    Actions.delay(i * delayBetweenCards),// Delay before the card moves
                    Actions.parallel(
                        Actions.moveTo(cardX, y, 0.5f),// Move the card to its final position (0.5 seconds animation)
                        Actions.fadeIn(0.7f)
                    )
                )
            );

            // Update the x position for the next card
            startX += region.getRegionWidth() + spacing;
        }
    }

    public void moveCardsBackToDeck() {
        float delayBetweenCards = 0.2f; // Delay between animations

        float deckCoverY = stage.getHeight() / 2 - (float) 195 / 2;

        // Iterate over the cards
        for (Image cardImage : cardImages) {

            // Animation sequence
            cardImage.addAction(
                Actions.sequence(
                    Actions.delay(delayBetweenCards), // Delay for staggered movement
                    Actions.moveTo(20, deckCoverY, 0.5f), // Move card to deck
                    Actions.run(() -> {
                        cardImage.remove(); // Remove the card image from the stage
                        Gdx.app.log("PIGA21", "Card removed: " + cardImage);
                    })
                )
            );
        }
        cardImages.clear();
    }

    public void dealSplitCards(List<Card> cards, float xPosition) {
        // Define the delay between cards (in seconds)
        float delayBetweenCards = 0.2f; // 0.5 seconds delay

        // Iterate over the card regions
        for (int i = 0; i < cards.size(); i++) {
            final TextureRegion region = assetStore.getTextureRegion(cards.get(i));
            if (region == null) {
                System.err.println("Warning: TextureRegion for card index " + i + " is null. Skipping this card.");
                continue; // Skip null regions
            }

            // Create the card image
            Image cardImage = new Image(region);
            final float cardX = centerXPosition(cardImage);
            cardImage.setPosition(cardX, stage.getHeight() / 2 - (float) 195 / 2);
            stage.addActor(cardImage);
            cardImages.add(cardImage);

            // Create an action sequence for the card
            cardImage.getColor().a = 0;
            cardImage.addAction(
                Actions.sequence(
                    Actions.delay(i * delayBetweenCards),// Delay before the card moves
                    Actions.parallel(
                        Actions.moveTo(xPosition, y, 0.5f),// Move the card to its final position (0.5 seconds animation)
                        Actions.fadeIn(0.7f)
                    )
                )
            );
        }
    }

    public void addNewCardToSplitStage(Card card, List<Card> cards, String hand, Image image) {
        // Calculate the total width of all cards including spacing
        float totalCardWidth = calculateTotalCardWidth(cards);

        // Calculate the starting x position to center the cards

        float screenWidth;
        float startX;
        if (hand.equalsIgnoreCase("player")) {
            screenWidth = stage.getWidth() / 2; // Width of the left half
            startX = (screenWidth - totalCardWidth) / 2; // Center in the left half
        } else {
            screenWidth = stage.getWidth() / 2; // Width of the right half
            startX = (stage.getWidth() / 2) + ((screenWidth - totalCardWidth) / 2) - image.getWidth() / 4; // Center in the right half
        }

        // Calculate the x position for the new card
        float cardX = startX + (cards.size() - 1) * (assetStore.getTextureRegion(card).getRegionWidth() + spacing);

        // Create the card image
        Image cardImage = new Image(assetStore.getTextureRegion(card));
        cardImage.setPosition(20, deckYPosition()); // Start off-screen to the left

        // Set the initial opacity of the card to 0 (fully transparent)
        cardImage.getColor().a = 0;

        // Add the card to the stage
        stage.addActor(cardImage);
        cardImages.add(cardImage);

        // Create an action sequence for the card
        cardImage.addAction(
            Actions.sequence(
                Actions.parallel(
                    Actions.moveTo(cardX, y, 0.5f), // Move the card to its final position (0.5 seconds animation)
                    Actions.fadeIn(0.5f) // Fade in the card over 0.5 seconds
                )
            )
        );
    }

    /**
     * Center horizontal position on the x axis
     */
    public float centerXPosition(Image image) {
        return stage.getWidth() / 2 - image.getWidth() / 2;
    }

    /**
     * Get the center of the center of the left half of the stage.
     */
    public float centerOfCenterXPositionLeft(Image image) {
        return (stage.getWidth() / 4) - (image.getWidth() / 2) / 4;
    }

    /**
     * Get the center of the center of the right half of the stage.
     */
    public float centerOfCenterXPositionRight(Image image) {
        return (stage.getWidth() * 3 / 4) - (image.getWidth() / 2);
    }

    public void dealGameOverCards(Stage dealingStage, List<Card> cards, float startXPosition, float startYPosition) {
        // Define the delay between cards (in seconds)
        float delayBetweenCards = 0.2f; // 0.2 seconds delay

        // Iterate over the card regions
        for (int i = 0; i < cards.size(); i++) {
            final TextureRegion region = assetStore.getTextureRegion(cards.get(i));
            if (region == null) {
                System.err.println("Warning: TextureRegion for card index " + i + " is null. Skipping this card.");
                continue; // Skip null regions
            }

            // Create the card image
            Image cardImage = new Image(region);
            final float cardWidth = cardImage.getWidth();

            // Calculate the x position for the current card
            float cardX = startXPosition + (i * cardWidth * 0.5f); // Each card starts at half the width of the previous card
            float cardY = startYPosition; // All cards have the same Y position

            // Set the initial position of the card
            cardImage.setPosition(cardX, cardY);
            dealingStage.addActor(cardImage);
            cardImages.add(cardImage);

            // Create an action sequence for the card
            cardImage.getColor().a = 0;
            cardImage.addAction(
                Actions.sequence(
                    Actions.delay(i * delayBetweenCards), // Delay before the card moves
                    Actions.parallel(
                        Actions.moveTo(cardX, cardY, 0.5f), // Move the card to its final position (0.5 seconds animation)
                        Actions.fadeIn(0.7f)
                    )
                )
            );
        }
    }
}
