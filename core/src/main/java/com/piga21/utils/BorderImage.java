package com.piga21.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

public class BorderImage extends Image {
    public BorderImage(TextureRegion region) {
        super(region);
        this.setColor(Color.YELLOW); // Highlight color
    }
}
