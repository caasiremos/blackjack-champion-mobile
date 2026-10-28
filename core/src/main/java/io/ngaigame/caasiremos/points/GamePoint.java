package io.ngaigame.caasiremos.points;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;

public class GamePoint {

    public static String POINTS = "CHIPS: ";
    public static final String GAME_PREFS = "GamePrefs";
    public static final String GAME_POINTS = "gamePoints";
    public static final String API_TOKEN = "token";

    private int currentPoints;

    public GamePoint() {
        Preferences prefs = Gdx.app.getPreferences(GAME_PREFS);

        // Check if points are already stored, if not, initialize them
        if (!prefs.contains(GAME_POINTS)) {
            currentPoints = 10000; // starting points
            storePoints(currentPoints);
            Gdx.app.log("GamePoint", "First install detected, points initialized to 500.");
        } else {
            currentPoints = getPoints();
            Gdx.app.log("GamePoint", "Points loaded: " + currentPoints);
        }
    }

    public void storePoints(int points) {
        currentPoints = points;
        Preferences prefs = Gdx.app.getPreferences(GAME_PREFS);
        prefs.putInteger(GAME_POINTS, currentPoints);
        prefs.flush(); // Save the data
        Gdx.app.log("GamePoint", "Points stored: " + currentPoints);
    }

    public int getPoints() {
        Preferences prefs = Gdx.app.getPreferences(GAME_PREFS);
        return prefs.getInteger(GAME_POINTS, 500); // default 500 (safe fallback)
    }

    public String getApiToken() {
        Preferences prefs = Gdx.app.getPreferences(GAME_PREFS);
        return prefs.getString(API_TOKEN, "");
    }

    public void addPoints(int score) {
        currentPoints += score;
        storePoints(currentPoints);
    }

    public void subtractPoints(int score) {
        currentPoints -= score;

        // Ensure points don't go below 0
        if (currentPoints < 0) {
            currentPoints = 0;
        }

        storePoints(currentPoints); // Save the updated points
    }

    public void newScore(Label pointsLabel, int scoreToAdd) {
        int previousPoints = currentPoints;
        int targetPoints = previousPoints + scoreToAdd;

        // Don't animate negative point changes directly
        if (scoreToAdd < 0) {
            // Simply update the label without animation for negative changes
            pointsLabel.setText("Points: " + targetPoints);
        } else {
            // Animate points for positive changes
            animatePointsCounter(pointsLabel, previousPoints, targetPoints, 1.5f);
        }

        storePoints(targetPoints); // Save the updated points
    }

    /**
     * Animates the points counter from the current value to the target value.
     *
     * @param pointsLabel Label to update
     * @param startPoints Starting points
     * @param endPoints   Ending points
     * @param duration    Duration of the animation
     */
    public void animatePointsCounter(Label pointsLabel, int startPoints, int endPoints, float duration) {
        final int[] currentDisplayPoints = {startPoints};
        int totalDelta = endPoints - startPoints;

        if (totalDelta == 0) {
            pointsLabel.setText(POINTS + endPoints);
            return; // No need to animate
        }

        int steps = Math.abs(totalDelta) / 10; // Try 10 points per step
        if (steps < 1) steps = 1; // Minimum one step

        float delayPerStep = duration / steps;
        int increment = totalDelta / steps;

        Gdx.app.log("GamePoint", "Animating points from " + startPoints + " to " + endPoints +
            " over " + steps + " steps (increment: " + increment + ")");

        pointsLabel.addAction(Actions.sequence(
            Actions.run(() -> pointsLabel.setText(POINTS + currentDisplayPoints[0])),
            Actions.repeat(steps, Actions.sequence(
                Actions.delay(delayPerStep),
                Actions.run(() -> {
                    currentDisplayPoints[0] += increment;
                    pointsLabel.setText(POINTS + currentDisplayPoints[0]);
                })
            )),
            Actions.run(() -> pointsLabel.setText(POINTS + endPoints)) // Ensure final value is exact
        ));
    }
}
