package io.ngaigame.caasiremos.utils;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import java.util.concurrent.atomic.AtomicBoolean;

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

    public static void showMenuItemDialog(Stage stage,
                                   CommonUtilInterface commonUtilInterface,
                                   Image menusImage,
                                   Image menuImage,
                                   Image musicOffImage,
                                   Image musicOnImage,
                                   Image soundOnImage,
                                   Image soundsOffImage,
                                   Image shareImage,
                                   Image exitImage,
                                   Image rewardButtonImage,
                                   boolean overlayShowingFlag) {

        AtomicBoolean overlayShowing = new AtomicBoolean(overlayShowingFlag);

        menusImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                menusImage.addAction(Actions.scaleTo(1.05f, 1.05f, 0.1f));
                menuImage.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                menusImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));

                OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage);

                float buttonWidth = 370f, buttonHeight = 120f;

                Table table = new Table();
                table.setFillParent(true);
                table.center();

                table.add(musicOffImage).size(buttonWidth, buttonHeight).padBottom(20f).row();
                table.add(musicOnImage).size(buttonWidth, buttonHeight).padBottom(20f).row();
                table.add(soundOnImage).size(buttonWidth, buttonHeight).padBottom(20f).row();
                table.add(soundsOffImage).size(buttonWidth, buttonHeight).padBottom(20f).row();
                table.add(shareImage).size(buttonWidth, buttonHeight).padBottom(20f).row();

                Table exitTable = new Table();
                exitTable.setFillParent(true);
                exitTable.top().right();

                float marginTop = 100f;
                float marginEnd = 150f;
                float exitSize = 120f;

                exitTable.add(exitImage).size(exitSize).padTop(marginTop).padRight(marginEnd);

                overlay.addActor(table);
                overlay.addActor(exitTable);

                soundsOffImage.addListener(new ClickListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        soundsOffImage.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                        return true;
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                        soundsOffImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                        Settings.toggleSoundSettings(false);
                        commonUtilInterface.showLongToast("Game sound turned off!");
                    }
                });

                soundOnImage.addListener(new ClickListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        soundOnImage.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                        return true;
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                        soundOnImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                        Settings.toggleSoundSettings(true);
                        commonUtilInterface.showLongToast("Game sound turned on!");
                    }
                });

                musicOnImage.addListener(new ClickListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        musicOnImage.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                        return true;
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                        musicOnImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                        Settings.toggleMusicSettings(true);
                        commonUtilInterface.showLongToast("Game background music turned on!");
                    }
                });

                musicOffImage.addListener(new ClickListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        musicOffImage.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                        return true;
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                        musicOffImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                        Settings.toggleMusicSettings(false);
                        commonUtilInterface.showLongToast("Game background music turned off!");
                    }
                });

                shareImage.addListener(new ClickListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        shareImage.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                        return true;
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                        shareImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                        String shareText = "Check out this awesome Offline Blackjack Game.";
                        String shareSubject = "Awesome Offline Blackjack Game";

                        if (commonUtilInterface != null) {
                            commonUtilInterface.shareApp(shareText, shareSubject);
                        }
                    }
                });

                exitImage.addListener(new ClickListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        exitImage.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                        return true;
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                        rewardButtonImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                        if (overlayShowing.get()) {
                            overlayShowing.set(false);
                            CommonGameFunctions.hideOverlayScreen();
                        }
                    }
                });

                CommonGameFunctions.showOverlay();
                overlayShowing.set(true);
            }
        });
    }

}
