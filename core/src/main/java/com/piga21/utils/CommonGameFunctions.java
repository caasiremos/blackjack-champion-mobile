package com.piga21.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;

public class CommonGameFunctions {
    public static OverlayContainer overlayContainer;

    public static OverlayContainer initializeOverlay(Stage stage){
        return initializeOverlay(stage, 0.7f);
    }

    public static OverlayContainer initializeOverlay(Stage stage, float alpha){
        System.out.println("CommonGameFunctions.initializeOverlay(): Starting overlay initialization");
        hideOverlayScreen(true);

        Rectangle overlay = new Rectangle(stage.getWidth()/2, stage.getHeight()/2, stage.getWidth(), stage.getHeight(), Color.BLACK, false);
        overlay.setAlpha(alpha);

        overlayContainer = new OverlayContainer(stage.getWidth(), stage.getHeight());
        overlayContainer.addActor(overlay);

        overlayContainer.setScale(0.0f);
        stage.addActor(overlayContainer);
        
        System.out.println("CommonGameFunctions.initializeOverlay(): Overlay initialized and added to stage");

        return overlayContainer;
    }

    /**
     * True while a dialog (menu, game over, challenge) is on screen.
     */
    public static boolean isOverlayShowing(){
        return overlayContainer != null && overlayContainer.hasParent();
    }

    public static void showOverlay(){
        if(overlayContainer == null){
            System.err.println("CommonGameFunctions.showOverlay(): overlayContainer is null!");
            return;
        }
        System.out.println("CommonGameFunctions.showOverlay(): Adding scale action to overlay");
        overlayContainer.addAction(Actions.scaleTo(1.0f, 1.0f, 0.5f));
    }

    public static void hideOverlayScreen(){
        hideOverlayScreen(false);
    }

    public static void hideOverlayScreen(boolean fast){
        if(overlayContainer == null){
            return;
        }

        if(fast){
            overlayContainer.remove();
        }else{
            overlayContainer.addAction(Actions.sequence(Actions.scaleTo(0.0f, 0.0f, 0.5f), new Action() {
                @Override
                public boolean act(float delta) {
                    overlayContainer.remove();
                    return true;
                }
            }));
        }
    }
}
