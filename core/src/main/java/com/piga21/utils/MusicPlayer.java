package com.piga21.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.utils.Disposable;

public class MusicPlayer implements Disposable {
    private Music backgroundMusic;
    private boolean musicEnabled = true;
    private float volume = 0.3f;
    private boolean wasPlayingBeforeDisable = false;
    private boolean isInitialized = false;

    /**
     * Initializes the music player with the specified music file
     */
    public void initialize(String musicFilePath) {
        if (isInitialized) {
            return;
        }

        try {
            // Dispose of previous music if exists
            if (backgroundMusic != null) {
                backgroundMusic.dispose();
            }

            backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal(musicFilePath));
            backgroundMusic.setLooping(true);
            backgroundMusic.setVolume(volume);

            if (musicEnabled) {
                //backgroundMusic.play();
            }

            isInitialized = true;
        } catch (Exception e) {
            Gdx.app.error("MusicPlayer", "Error initializing music: " + e.getMessage());
        }
    }

    /**
     * Toggles music on/off
     */
    public void setMusicEnabled(boolean enabled) {
        if (this.musicEnabled == enabled || !isInitialized) {
            return;
        }

        this.musicEnabled = enabled;

        if (enabled) {
            if (!backgroundMusic.isPlaying()) {
               // backgroundMusic.play();
            }
            backgroundMusic.setVolume(volume);
        } else {
            wasPlayingBeforeDisable = backgroundMusic.isPlaying();
            backgroundMusic.pause();
        }
    }

    /**
     * Sets volume (0.0 to 1.0)
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume)); // Clamp between 0 and 1

        if (isInitialized && musicEnabled) {
            backgroundMusic.setVolume(this.volume);
        }
    }

    /**
     * Pauses music if playing
     */
    public void pause() {
        if (isInitialized && backgroundMusic.isPlaying()) {
            backgroundMusic.pause();
        }
    }

    /**
     * Resumes music if enabled and paused
     */
    public void resume() {
        if (isInitialized && musicEnabled && !backgroundMusic.isPlaying()) {
            backgroundMusic.play();
        }
    }

    /**
     * Stops and disposes of music resources
     */
    @Override
    public void dispose() {
        if (backgroundMusic != null) {
            if (backgroundMusic.isPlaying()) {
                backgroundMusic.stop();
            }
            backgroundMusic.dispose();
            backgroundMusic = null;
        }
        isInitialized = false;
    }

    // Getters
    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public float getVolume() {
        return volume;
    }

    public boolean isPlaying() {
        return isInitialized && backgroundMusic.isPlaying();
    }
}
