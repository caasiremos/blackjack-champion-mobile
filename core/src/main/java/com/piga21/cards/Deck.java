package com.piga21.cards;

import com.badlogic.gdx.Gdx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class Deck {
    private List<Card> cards;
    private Random random;

    public Deck() {
        cards = new ArrayList<>();
        random = new Random();
        initializeDeck();
    }

    /**
     * Initializes the deck with all cards and shuffles it.
     */
    private void initializeDeck() {
        cards.clear(); // Clear any existing cards
        cards.addAll(Arrays.asList(Card.values())); // Add all cards from the enum
        shuffle(); // Shuffle the deck
        Gdx.app.log("DECK", cards.toString());
        Gdx.app.log("DECK", String.valueOf(cards.size()));
    }

    /**
     * Shuffles the deck.
     */
    public void shuffle() {
        Collections.shuffle(cards, random);
    }

    /**
     * Draws a random card from the deck.
     *
     * @return A randomly drawn card.
     * @throws IllegalStateException if the deck is empty.
     */
    public Card getRandomCard() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("Deck is empty! Cannot draw a card.");
        }
        return cards.remove(random.nextInt(cards.size()));
    }

    /**
     * Adds a list of cards back to the deck.
     *
     * @param cardList The list of cards to add.
     */
    public void addCards(List<Card> cardList) {
        cards.addAll(cardList);
    }

    /**
     * Finds a card by its identifier (name).
     *
     * @param cardIdentifier The name of the card to find.
     * @return An Optional containing the card if found, or empty if not found.
     */
    public Optional<Card> getCard(String cardIdentifier) {
        return cards.stream()
            .filter(card -> card.name().equals(cardIdentifier))
            .findFirst();
    }

    public void allCards() {
        cards.addAll(Arrays.asList(Card.values()));
    }

    /**
     * Resets the deck by reinitializing and shuffling it.
     */
    public void reset() {
        initializeDeck();
    }

    /**
     * Returns the number of cards remaining in the deck.
     *
     * @return The size of the deck.
     */
    public int size() {
        return cards.size();
    }

    /**
     * Checks if the deck is empty.
     *
     * @return True if the deck is empty, false otherwise.
     */
    public boolean isEmpty() {
        return cards.isEmpty();
    }
}
