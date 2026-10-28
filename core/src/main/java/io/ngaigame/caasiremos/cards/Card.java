package io.ngaigame.caasiremos.cards;

import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;

public enum Card {
    ACE_SPADE(11), TWO_SPADE(2), THREE_SPADE(3), FOUR_SPADE(4), FIVE_SPADE(5),
    SIX_SPADE(6), SEVEN_SPADE(7), EIGHT_SPADE(8), NINE_SPADE(9), TEN_SPADE(10),
    JACK_SPADE(10), QUEEN_SPADE(10), KING_SPADE(10),

    ACE_FLOWER(11), TWO_FLOWER(2), THREE_FLOWER(3), FOUR_FLOWER(4), FIVE_FLOWER(5),
    SIX_FLOWER(6), SEVEN_FLOWER(7), EIGHT_FLOWER(8), NINE_FLOWER(9), TEN_FLOWER(10),
    JACK_FLOWER(10), QUEEN_FLOWER(10), KING_FLOWER(10),

    ACE_HEART(11), TWO_HEART(2), THREE_HEART(3), FOUR_HEART(4), FIVE_HEART(5),
    SIX_HEART(6), SEVEN_HEART(7), EIGHT_HEART(8), NINE_HEART(9), TEN_HEART(10),
    JACK_HEART(10), QUEEN_HEART(10), KING_HEART(10),

    ACE_DIAMOND(11), TWO_DIAMOND(2), THREE_DIAMOND(3), FOUR_DIAMOND(4), FIVE_DIAMOND(5),
    SIX_DIAMOND(6), SEVEN_DIAMOND(7), EIGHT_DIAMOND(8), NINE_DIAMOND(9), TEN_DIAMOND(10),
    JACK_DIAMOND(10), QUEEN_DIAMOND(10), KING_DIAMOND(10);

    private final int value;

    Card(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    // Check if the card is an Ace
    public boolean isAce() {
        return this == ACE_HEART || this == ACE_FLOWER || this == ACE_SPADE || this == ACE_DIAMOND;
    }

    // Calculate the total value of a cards, adjusting for Aces
    public static int sumCardValues(List<Card> cards) {
        int totalValue = 0;
        int aceCount = 0;

        // First, calculate the total value assuming all Aces are worth 11
        for (Card card : cards) {
            if (card.isAce()) {
                aceCount++;
            }
            totalValue += card.getValue();
        }

        // Adjust for Aces if the total value exceeds 21
        while (totalValue > 21 && aceCount > 0) {
            totalValue -= 10; // Change an Ace from 11 to 1
            aceCount--;
        }

        return totalValue;
    }

    /**
     * Convert a string (e.g., "ACE_HEARTS") to the corresponding Card enum.
     * Throws IllegalArgumentException if the card is invalid.
     */
    public static Card fromString(String cardStr) {
        try {
            return Card.valueOf(cardStr); // Directly match enum name
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid card: " + cardStr, e);
        }
    }

    public static List<Card> parseCardList(JsonValue jsonCards) {
        List<Card> cards = new ArrayList<>();
        for (JsonValue cardValue : jsonCards) {
            String cardStr = cardValue.asString(); // e.g., "ACE_HEARTS"
            cards.add(Card.fromString(cardStr));
        }
        return cards;
    }
}
