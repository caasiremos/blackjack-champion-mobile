package io.ngaigame.caasiremos.auth;

public interface GoogleSignInInterface {
    /**
     * Start signing in now, showing the Google account picker if needed.
     */
    void signIn();

    /**
     * True once the player has a server account (not just a Google sign-in).
     */
    boolean isSignedIn();

    /**
     * The server stopped accepting the saved login; sign in again.
     */
    void onSessionRejected();

    String getDisplayName();

    String getEmail();

    String getUserId();
}
