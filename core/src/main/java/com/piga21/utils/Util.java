package com.piga21.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;

import java.util.List;
import java.util.Random;

import com.piga21.AssetStore;
import com.piga21.Piga21Game;
import com.piga21.cards.Card;

public class Util {
    public static final String GAME_PREFS = "GamePrefs";
    public static final String API_TOKEN = "token";
    public static final String USER_ID = "token";

    public static String getApiToken() {
        Preferences prefs = Gdx.app.getPreferences(GAME_PREFS);
        return prefs.getString(API_TOKEN, "");
    }

    public static int getUserId() {
        Preferences prefs = Gdx.app.getPreferences(GAME_PREFS);
        return prefs.getInteger(Constants.PLAYER_ID, 0);
    }

    public static String getCurrentGameId() {
        Preferences prefs = Gdx.app.getPreferences(GAME_PREFS);
        return prefs.getString(Constants.GAME_ID, "");
    }

    public static void storeCurrentGameId(String currentGameId) {
        Preferences prefs = Gdx.app.getPreferences(GAME_PREFS);
        prefs.putString(Constants.GAME_ID, currentGameId);
        prefs.flush();
    }

    // Function to generate a random threshold between start number and end number
    public static int getRandomThreshold(int start, int end) {
        return new Random().nextInt(start) + end;
    }

    public static void handCardsCounter(List<Card> cards, String hand, Label label) {
        if (hand.equalsIgnoreCase("dealer")) {
            counterAnimation(label, cards);
        }
        if (hand.equalsIgnoreCase("player")) {
            counterAnimation(label, cards);
        }
        if (hand.equalsIgnoreCase("splitHandOne")) {
            counterAnimation(label, cards);
        }
        if (hand.equalsIgnoreCase("splitHandTwo")) {
            counterAnimation(label, cards);
        }
    }

    private static void counterAnimation(Label label, List<Card> cards) {
        int newValue = Card.sumCardValues(cards);
        label.addAction(
            Actions.sequence(
                Actions.fadeOut(0.1f),
                Actions.run(() -> label.setText(String.valueOf(newValue))),
                Actions.fadeIn(0.1f)
            )
        );
        label.setVisible(true);
    }

    public static void layoutButtonsAtBottomRightHalf(Stage stage, Image... buttons) {
        float startX = stage.getWidth() / 2; // Start from middle of screen
        float endX = stage.getWidth();// End at screen's right edge
        float availableWidth = endX - startX;

        // Filter visible buttons
        Array<Image> visibleButtons = new Array<>();
        for (Image button : buttons) {
            if (button.isVisible()) {
                visibleButtons.add(button);
            }
        }

        int count = visibleButtons.size;
        if (count == 0) return;  // No visible buttons, nothing to position

        // Calculate spacing between buttons
        float spacing = availableWidth / (count + 1);  // +1 because we leave half-spacing at each end

        // Y position (adjust as needed)
        float y = 20;  // 20 pixels from the bottom, for example

        // Position each button
        for (int i = 0; i < count; i++) {
            Image button = visibleButtons.get(i);

            float centerX = startX + spacing * (i + 1);
            float buttonX = centerX - button.getWidth() / 2;

            button.addAction(Actions.moveTo(buttonX, y, 0.8f, Interpolation.swingOut));
            button.addAction(Actions.moveTo(buttonX, y, 0.8f, Interpolation.swingOut));
        }
    }

    public static void blackJackEffect(Stage stage) {
        // Array to hold the 10 different textures
        Array<Texture> textures = new Array<>();

        // Load 10 different textures (replace with your actual texture paths)
        textures.add(new Texture("confetti/one.png"));
        textures.add(new Texture("confetti/two.png"));
        textures.add(new Texture("confetti/three.png"));
        textures.add(new Texture("confetti/four.png"));
        textures.add(new Texture("confetti/five.png"));
        textures.add(new Texture("confetti/six.png"));
        textures.add(new Texture("confetti/seven.png"));
        textures.add(new Texture("confetti/eight.png"));
        textures.add(new Texture("confetti/nine.png"));
        textures.add(new Texture("confetti/ten.png"));

        // Create 10 Image actors and set their initial positions
        for (int i = 0; i < 10; i++) {
            Image textureImage = new Image(textures.get(i)); // Use a different texture for each Image

            // Set initial position at the bottom of the screen with random X
            float startX = (float) (Math.random() * stage.getWidth()); // Random X position
            float startY = 0; // Bottom of the screen
            textureImage.setPosition(startX, startY);

            // Set target position at the top of the screen with random X and Y
            float targetX = (float) (Math.random() * stage.getWidth()); // Random X position
            float targetY = (float) (Math.random() * stage.getHeight()); // Random Y position

            // Add the texture to the stage
            stage.addActor(textureImage);

            // Add a move action to the texture
            textureImage.addAction(
                Actions.sequence(
                    Actions.moveTo(targetX, targetY, 1.5f, Interpolation.smooth), // Move to target position in 2 seconds
                    Actions.removeActor() // Remove the texture from the stage after the animation
                )
            );
        }
    }

    public static void moveDealButton(Image dealImage, Stage stage) {
        dealImage.setVisible(true);
        dealImage.addAction(
            Actions.sequence(
                Actions.parallel(
                    Actions.moveTo(
                        stage.getWidth() / 2 - dealImage.getWidth() / 2,
                        50,
                        0.5f,
                        Interpolation.smooth
                    ),
                    Actions.scaleTo(2f, 2f, 0.5f, Interpolation.smooth) // Scale up slightly
                ),
                Actions.scaleTo(1.7f, 1.7f, 0.25f, Interpolation.smooth)   // Scale back to normal
            )
        );
    }

    public static void buttonSize(Image... buttons) {
        for (Image button : buttons) {
            button.setSize(170, 200);
        }
    }

    public static void startCountdown(Label timeLabel, Runnable onFinish) {
        final int[] countdown = {5};
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                Gdx.app.postRunnable(() -> {
                    if (countdown[0] > 0) {
                        timeLabel.setText(String.valueOf(countdown[0]));
                        countdown[0]--;
                    } else {
                        onFinish.run();
                    }
                });
            }
        }, 0, 1, 5); // Delay 0, interval 1 second, repeat 5 times
    }

    /**
     * Top-left "< BACK" button that does the same as the Android back button.
     */
    public static Label addBackButton(Stage stage, AssetStore assetStore, Piga21Game game) {
        Label back = new Label("< BACK", new Label.LabelStyle(assetStore.dialogFont, Color.valueOf("#F4B331")));
        back.pack();
        back.setPosition(40, stage.getHeight() - back.getHeight() - 30);
        back.setOrigin(back.getWidth() / 2, back.getHeight() / 2);
        back.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                back.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                back.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                game.goBack();
            }
        });
        stage.addActor(back);
        return back;
    }
}
