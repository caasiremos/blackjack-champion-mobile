package com.piga21.utils;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;


public class Settings {
    public static final String SOUND_PREFS = "SoundPref";
    public static final String SOUND = "sound";

    public static void toggleSoundSettings(boolean setting) {
        Preferences prefs = Gdx.app.getPreferences(SOUND_PREFS);
        prefs.putBoolean(SOUND, setting);
        prefs.flush();
    }

    public static boolean getSoundSetting() {
        Preferences prefs = Gdx.app.getPreferences(SOUND_PREFS);
        return prefs.getBoolean(SOUND, true);
    }

    public static void toggleMusicSettings(boolean musicSettings){
        MusicPlayer musicPlayer = new MusicPlayer();
        musicPlayer.setMusicEnabled(musicSettings);
    }

    public static void pauseMusic(){
        MusicPlayer musicPlayer = new MusicPlayer();
        musicPlayer.pause();
    }

    public static void resumeMusic(){
        MusicPlayer musicPlayer = new MusicPlayer();
        musicPlayer.resume();
    }
}
