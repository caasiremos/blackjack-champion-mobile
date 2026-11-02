package com.piga21.cards;

import com.google.gson.JsonArray;

import java.util.ArrayList;
import java.util.List;


public class CardMapper {

    /**
     * Converts a single card name string to a Card enum.
     * Example: "NINE_DIAMOND" -> Card.NINE_DIAMOND
     */
    public static Card fromString(String cardName) {
        return Card.fromString(cardName);
    }

    /**
     * Converts a list of card name strings to a list of Card enums.
     * Example: ["NINE_DIAMOND", "TEN_HEART"]
     */
    public static List<Card> fromStringList(List<String> cardNames) {
        List<Card> cards = new ArrayList<>();
        for (String name : cardNames) {
            cards.add(Card.fromString(name));
        }
        return cards;
    }

    /**
     * Converts a Gson JsonArray to a list of Card enums.
     * Example: JsonArray ["NINE_DIAMOND", "TEN_HEART"]
     */
    public static List<Card> fromJsonArray(JsonArray jsonArray) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            String cardStr = jsonArray.get(i).getAsString();
            cards.add(Card.fromString(cardStr));
        }
        return cards;
    }
}
