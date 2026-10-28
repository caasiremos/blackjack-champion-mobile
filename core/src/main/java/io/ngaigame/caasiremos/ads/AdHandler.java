package io.ngaigame.caasiremos.ads;

import org.jetbrains.annotations.Nullable;

public interface AdHandler {

    int BANNER_POSITION_TOP = 0;
    int BANNER_POSITION_BOTTOM = 1;
    int BANNER_POSITION_TOP_LEFT = 2;
    int BANNER_POSITION_TOP_RIGHT = 3;
    int BANNER_POSITION_BOTTOM_LEFT = 4;
    int BANNER_POSITION_BOTTOM_RIGHT = 5;
    int BANNER_POSITION_CENTER = 6;
    void showBannerAd();

    void loadBannerAd();

    void hideBannerAd();
    void showInterstitialAd();
    void loadInterstitialAd();

    void loadRewardedAd();

    void setBannerPosition(int gravity, int position);

    void showRewardedAd(@Nullable Runnable onRewardComplete);
}
