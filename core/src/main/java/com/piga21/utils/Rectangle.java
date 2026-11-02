package com.piga21.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;

public class Rectangle extends Actor {
    private Texture texture;
    public boolean maintainAspectRatio = true;

    public Rectangle(){}

    public Rectangle(float width, float height, Color color) {
        this(0, 0, width, height, color, true);
    }

    public Rectangle(float x, float y, float width, float height, Color color) {
        this(x, y, width, height, color, true);
    }

    public Rectangle(float x, float y, float width, float height, Color color, boolean aspectRatio) {
        maintainAspectRatio = aspectRatio;

        createTexture((int)width, (int)height, color);

        setWidth(width);
        setHeight(height);
        setOrigin(Align.center);
        setPosition(x, y);
    }

    private void createTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fillRectangle(0, 0, width, height);
        texture = new Texture(pixmap);
        pixmap.dispose();
    }

    public void setAlpha(float alpha){
        this.getColor().a = alpha;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color color = getColor();
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
        batch.draw(texture, getX(), getY(), getWidth(), getHeight());
    }

    @Override
    public void setPosition(float x, float y) {
        x -= getWidth()/2;
        y -= getHeight()/2;

        super.setPosition(x, y);
    }
}
