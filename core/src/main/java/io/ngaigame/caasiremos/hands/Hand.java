package io.ngaigame.caasiremos.hands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.ngaigame.caasiremos.cards.Card;

public class Hand {
    public List<Card> cards;

    public Hand() {
        cards = new ArrayList<>();
    }

    public void hit(Card c) {
        cards.add(c);
    }

    public int getSum() {
        return Card.sumCardValues(cards);
    }

    public void clear() {
        cards.clear(); // Clear the ArrayList
    }

    public ArrayList<Card> getCards() {
        return (ArrayList<Card>) cards;
    }

    public void addCard(Card card){
        cards.add(card);
    }


    /**
     * Checks if there is a pair of cards with a value of 10.
     */
    public boolean hasSameValuePairOf10(List<Card> cards) {
        int count = 0;

        for (Card card : cards) {
            if (card.getValue() == 10) {
                count++;
                if (count == 2) {
                    return true; // Found a pair of 10s
                }
            }
        }

        return false; // No pair of 10s found
    }

    /**
     * if two cards have the same values, split them into two list, one will be playerhand cards
     * and the other will be split hand cards
     */
    public void splitSameValueCardPair(List<Card> playerCards, List<Card> splitCard) {
        Map<Integer, Card> seenCards = new HashMap<>();

        Iterator<Card> iterator = playerCards.iterator();
        while (iterator.hasNext()) {
            Card card = iterator.next();
            int value = card.getValue();

            if (seenCards.containsKey(value)) {
                // Move one card to `movedCards`
                splitCard.add(card);
                iterator.remove(); // Remove it from `playerCards`
                return; // Stop after finding the first pair
            } else {
                seenCards.put(value, card);
            }
        }
    }
}
