package io.ngaigame.caasiremos;

/**
 * Implemented by screens that need to react to the Android back button (or the on-screen
 * back button) before the game navigates to the previous screen.
 */
public interface BackHandler {
    /**
     * @return true if the screen handled it (closed a dialog, asked for confirmation, ...)
     * and the game should stay on this screen; false to go back to the previous screen
     */
    boolean onBack();
}
