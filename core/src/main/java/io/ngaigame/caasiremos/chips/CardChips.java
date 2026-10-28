package io.ngaigame.caasiremos.chips;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

public class CardChips extends Image {
    public float originalX, originalY;
    public int amount;

    public CardChips(TextureRegion tr, int am, float x, float y){
        super(tr);

        originalX = x;
        originalY = y;

        amount = am;
    }
}
