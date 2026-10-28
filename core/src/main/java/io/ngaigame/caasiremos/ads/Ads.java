package io.ngaigame.caasiremos.ads;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.google.gson.JsonObject;

import java.util.concurrent.atomic.AtomicBoolean;

import io.ngaigame.caasiremos.points.GamePoint;
import io.ngaigame.caasiremos.screens.ClassicGamePlayScreen;
import io.ngaigame.caasiremos.utils.CommonGameFunctions;
import io.ngaigame.caasiremos.utils.OverlayContainer;
import io.ngaigame.caasiremos.utils.PlayerApi;
import io.ngaigame.caasiremos.utils.Util;

public class Ads {

    // Call this function at the end of each game round
    public static void showInterstitialAd(AdHandler adHandler, int gamesPlayed, int nextAdTrigger) {
        gamesPlayed++;

        if (gamesPlayed >= nextAdTrigger) {
            if (adHandler != null) {
                adHandler.showInterstitialAd();
            }

            gamesPlayed = 0;  // Reset game count after showing the ad
            nextAdTrigger = Util.getRandomThreshold(6, 10);// Random number between 10 (inclusive) and 15 (inclusive)
        }
    }

    public static void showBannerAd(AdHandler adHandler, int bannerAdGamesPlayed, int nextBannerAdTrigger) {
        bannerAdGamesPlayed++;
        if (bannerAdGamesPlayed >= nextBannerAdTrigger) {
            if (adHandler != null) {
                adHandler.loadBannerAd();
            }

           bannerAdGamesPlayed = 0;  // Reset game count after showing the ad
            nextBannerAdTrigger = Util.getRandomThreshold(4, 2);  // Set a new random threshold between 2 and 4
        }
    }

    public static void showRewardAdsDialog(
        ClassicGamePlayScreen classicGamePlayScreen,
        Stage stage,
        GamePoint gamePoint,
        AdHandler adHandler,
        Actor chipTitle,
        Image chipsImage,
        Actor watchBonusText,
        Image rewardButtonImage,
        Image exitImage,
        Label pointsLabel,
        boolean overlayShowing
    ) {
        OverlayContainer overlay = CommonGameFunctions.initializeOverlay(stage);
        AtomicBoolean overlayShowingFlag = new AtomicBoolean(overlayShowing);

        float chipsWidth = 500f, chipsHeight = 400f;
        float buttonWidth = 450f, buttonHeight = 160f;

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        table.add(chipTitle).padBottom(40f).row();
        table.add(chipsImage).size(chipsWidth, chipsHeight).padBottom(40f).row();
        table.add(watchBonusText).padBottom(40f).row();
        table.add(rewardButtonImage).size(buttonWidth, buttonHeight).row();

        Table exitTable = new Table();
        exitTable.setFillParent(true);
        exitTable.top().right();

        float marginTop = 150f;
        float marginEnd = 150f;
        float exitSize = 120f;

        exitTable.add(exitImage).size(exitSize).padTop(marginTop).padRight(marginEnd);

        overlay.addActor(table);
        overlay.addActor(exitTable);

        rewardButtonImage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                rewardButtonImage.addAction(Actions.scaleTo(1.1f, 1.1f, 0.1f));
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                rewardButtonImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));

                // The server credits the reward and returns the new balance
                PlayerApi.Callback credit = new PlayerApi.Callback() {
                    @Override
                    public void onSuccess(JsonObject player) {
                        pointsLabel.setText(GamePoint.POINTS + gamePoint.getPoints());
                        pointsLabel.layout();
                        classicGamePlayScreen.newGame();
                    }

                    @Override
                    public void onFailure(String message) {
                        classicGamePlayScreen.commonUtilInterface.showToast(PlayerApi.readableMessage(message));
                    }
                };

                if (adHandler != null) {
                    adHandler.showRewardedAd(() -> Gdx.app.postRunnable(() -> PlayerApi.claimAdReward(credit)));
                } else {
                    PlayerApi.claimAdReward(credit);
                }

                if (overlayShowingFlag.get()) {
                    overlayShowingFlag.set(false);
                    CommonGameFunctions.hideOverlayScreen();
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
                exitImage.addAction(Actions.scaleTo(1f, 1f, 0.1f));
                if (overlayShowingFlag.get()) {
                    overlayShowingFlag.set(false);
                    CommonGameFunctions.hideOverlayScreen();
                    classicGamePlayScreen.newGame();
                }
            }
        });

        CommonGameFunctions.showOverlay();
        overlayShowingFlag.set(true);
    }
}
