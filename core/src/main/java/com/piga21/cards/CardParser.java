package com.piga21.cards;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import com.piga21.utils.Util;

public class CardParser {

    public static class GameCards {
        public List<String> dealerCards;
        public List<String> opponentCards;

        public GameCards(List<String> dealerCards, List<String> opponentCards) {
            this.dealerCards = dealerCards;
            this.opponentCards = opponentCards;
        }
    }

    public static GameCards parseGameCards(String jsonString) {
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        String gameId = json.get("game_id").getAsString();
        Util.storeCurrentGameId(gameId);
        JsonObject initialState = json.getAsJsonObject("initial_state");

        JsonArray dealerArray = initialState.getAsJsonArray("dealer_cards");
        JsonArray opponentArray = initialState.getAsJsonArray("opponent_cards");

        List<String> dealerCards = new ArrayList<>();
        List<String> opponentCards = new ArrayList<>();

        for (JsonElement card : dealerArray) {
            dealerCards.add(card.getAsString());
        }

        for (JsonElement card : opponentArray) {
            opponentCards.add(card.getAsString());
        }

        return new GameCards(dealerCards, opponentCards);
    }

    public static String parseHitCard(String jsonString) {
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        if (json.has("game_state")) {
            JsonObject gameState = json.getAsJsonObject("game_state");
            if (gameState.has("hit_card") && !gameState.get("hit_card").isJsonNull()) {
                return gameState.get("hit_card").getAsString();
            }
        }
        return null; // No hit_card present
    }

    public static String gameStatus(String jsonString){
        JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
        if (jsonObject.has("game_state")) {
            JsonObject gameState = jsonObject.getAsJsonObject("game_state");
            if (gameState.has("status") && !gameState.get("status").isJsonNull()){
                return gameState.get("status").getAsString();
            }
        }
        return null;
    }
}

