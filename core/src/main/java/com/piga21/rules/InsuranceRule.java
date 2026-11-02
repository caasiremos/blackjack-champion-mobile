package com.piga21.rules;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import com.piga21.AssetStore;
import com.piga21.screens.ClassicGamePlayScreen;
import com.piga21.cards.Deck;
import com.piga21.hands.PlayerHand;

public class InsuranceRule extends ClassicGamePlayScreen {
    AssetStore assetStore;
    Deck deck;
    PlayerHand playerHand;
    Stage stage;
    public InsuranceRule(Stage stage, AssetStore assetStore, Deck deck, PlayerHand playerHand) {
        super(assetStore);
        this.stage = stage;
        this.assetStore = assetStore;
        this.deck = deck;
        this.playerHand = playerHand;
    }

    public void insuranceImplementation(Image insuranceImage) {
        insuranceImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                insuranceImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                insuranceImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
            }
        });
    }
}
