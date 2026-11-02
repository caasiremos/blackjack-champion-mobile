package com.piga21.utils;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.utils.Align;

public class OverlayContainer extends WidgetGroup {
    public OverlayContainer(float w, float h){
        super();
        setColor(0, 0, 0, 1.0f);
        setSize(w, h);
        setOrigin(Align.center);
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        super.draw(batch, parentAlpha);
        applyTransform(batch, computeTransform());
        drawChildren(batch, parentAlpha);
        resetTransform(batch);
    }
}
