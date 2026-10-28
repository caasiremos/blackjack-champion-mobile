package io.ngaigame.caasiremos.android;

import static io.ngaigame.caasiremos.points.GamePoint.GAME_POINTS;
import static io.ngaigame.caasiremos.points.GamePoint.GAME_PREFS;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import io.ngaigame.caasiremos.BuildConfig;
import io.ngaigame.caasiremos.MainBlackJack;
import io.ngaigame.caasiremos.ads.AdHandler;
import io.ngaigame.caasiremos.ads.RewardListener;

public class AndroidAdHandler implements AdHandler {
    private static final String TAG = "AndroidAdHandler";
    private AdView adView;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private final Activity activity;
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    RewardListener rewardListener;
    private FrameLayout rootLayout;


    public AndroidAdHandler(Activity activity) {
        this.activity = activity;
        initialize();
    }

    public void setRootLayout(FrameLayout layout) {
        this.rootLayout = layout;
    }


    public void initialize() {
        // Initialize SharedPreferences
        if (activity != null) {
            prefs = activity.getSharedPreferences(GAME_PREFS, Context.MODE_PRIVATE);
            editor = prefs.edit();
        } else {
            Log.e(TAG, "Activity context is null, cannot initialize SharedPreferences.");
        }
    }

    @Override
    public void showBannerAd() {
        activity.runOnUiThread(() -> {
            if (adView != null) {
                adView.setVisibility(View.VISIBLE);
                Log.d(TAG, "Banner ad shown");
            } else {
                Log.e(TAG, "Banner ad is null");
            }
        });
    }

    @Override
    public void hideBannerAd() {
        activity.runOnUiThread(() -> {
            if (adView != null) {
                adView.setVisibility(View.GONE);
                Log.d(TAG, "Banner ad hidden");
            } else {
                Log.e(TAG, "Banner ad is null");
            }
        });
    }

    // Store the earned reward in SharedPreferences
    public void storeReward(int points) {
        if (prefs != null && editor != null) {
            editor.putInt(GAME_POINTS, points);
            editor.apply();  // Apply the changes asynchronously
            Log.d(TAG, "Reward of " + points + " saved to SharedPreferences");
        } else {
            Log.e(TAG, "SharedPreferences is not initialized.");
        }
    }

    public int getReward() {
        if (prefs != null) {
            Log.e(TAG, "current points." + prefs.getInt(GAME_POINTS, 0));
            // Retrieve the stored reward value from SharedPreferences
            return prefs.getInt(GAME_POINTS, 0);  // Default value is 0 if no reward is saved
        }
        Log.e(TAG, "SharedPreferences is not initialized.");
        return 0;
    }

    @Override
    public void showInterstitialAd() {
        activity.runOnUiThread(() -> {
            if (interstitialAd != null) {
                interstitialAd.show(activity);
                Log.d(TAG, "Interstitial ad shown");

                // Reload the interstitial ad after showing it
                loadInterstitialAd();
            } else {
                Log.e(TAG, "Interstitial ad is null, reloading...");
                loadInterstitialAd();
            }
        });
    }

    @Override
    public void loadInterstitialAd() {
        activity.runOnUiThread(() -> {
            AdRequest adRequest = new AdRequest.Builder().build();

            // Choose ad unit ID based on the build type
            String adUnitId;
            if (BuildConfig.DEBUG_MODE) {
                adUnitId = "ca-app-pub-3940256099942544/1033173712"; // Test ad unit ID
            } else {
                adUnitId = "ca-app-pub-4983287985456951/3154839428"; // Production ad unit ID
            }

            // Load the interstitial ad
            InterstitialAd.load(activity, adUnitId, adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                        Log.d(TAG, "Interstitial ad loaded");
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        interstitialAd = null;
                        Log.e(TAG, "Interstitial ad failed to load: " + loadAdError.getMessage());
                    }
                });
        });
    }


    @Override
    public void showRewardedAd(@Nullable Runnable onRewardComplete) {
        activity.runOnUiThread(() -> {
            if (rewardedAd != null) {
                rewardedAd.show(activity, new OnUserEarnedRewardListener() {
                    @Override
                    public void onUserEarnedReward(@NonNull RewardItem reward) {
                        // The server credits the chips (PlayerApi.claimAdReward in the callback)
                        int totalPoints = getReward();

                        // Notify the listener
                        if (rewardListener != null) {
                            rewardListener.onRewardReceived(totalPoints);
                        }

                        // Call the callback function if it's provided
                        if (onRewardComplete != null) {
                            activity.runOnUiThread(onRewardComplete);  // Execute the callback on the main thread
                        }
                    }
                });
                Log.d(TAG, "Rewarded ad shown");

                // Load a new rewarded ad for the future
                loadRewardedAd();
            } else {
                Log.e(TAG, "Rewarded ad is null, reloading...");
                loadRewardedAd();  // Reload the ad if it's not available
            }
        });
    }


    @Override
    public void loadRewardedAd() {
        activity.runOnUiThread(() -> {
            // Create an AdRequest
            AdRequest adRequest = new AdRequest.Builder().build();

            // Determine the correct Ad Unit ID based on build type
            String adUnitId;
            if (BuildConfig.DEBUG_MODE) {
                // Test Ad Unit ID provided by Google
                adUnitId = "ca-app-pub-3940256099942544/5224354917";
            } else {
                // Your production Ad Unit ID
                adUnitId = "ca-app-pub-4983287985456951/2394315732";
            }

            // Load the rewarded ad
            RewardedAd.load(activity, adUnitId, adRequest, new RewardedAdLoadCallback() {
                @Override
                public void onAdLoaded(RewardedAd ad) {
                    // Successfully loaded the rewarded ad
                    rewardedAd = ad;
                    Log.d(TAG, "Rewarded ad loaded");

                    // Optional: Log ad reward info (null until user watches the ad)
                    if (ad.getRewardItem() != null) {
                        Log.d(TAG, "Reward item type: " + ad.getRewardItem().getType());
                        Log.d(TAG, "Reward item amount: " + ad.getRewardItem().getAmount());
                    }

                    // Optional: Log ad metadata (may be empty depending on the network)
                    Log.d(TAG, "Ad metadata: " + ad.getAdMetadata());
                }

                @Override
                public void onAdFailedToLoad(LoadAdError loadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: " + loadAdError.getMessage());
                }
            });
        });
    }

    @Override
    public void loadBannerAd() {
        activity.runOnUiThread(() -> {
            if (adView == null) {
                adView = new AdView(activity);
                adView.setAdSize(AdSize.BANNER);
                String adUnitId;
                if (BuildConfig.DEBUG) {
                    // Test Ad Unit ID provided by Google
                    adUnitId = "ca-app-pub-3940256099942544/6300978111";
                } else {
                    // Your real production Ad Unit ID
                    adUnitId = "ca-app-pub-4983287985456951/7342571846";
                }

                adView.setAdUnitId(adUnitId);
                AdRequest adRequest = new AdRequest.Builder().build();
                adView.loadAd(adRequest);
                Log.d(TAG, "Banner ad loaded");

                FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
                );

                if (rootLayout != null) {
                    rootLayout.addView(adView, adParams);
                }

                adView.setVisibility(View.GONE); // Hide until you're ready
            }
        });
    }

    @Override
    public void setBannerPosition(int gravity, int marginBottom) {
        activity.runOnUiThread(() -> {
            if (adView != null) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) adView.getLayoutParams();

                if (params == null) {
                    params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    );
                }

                params.gravity = gravity;
                params.bottomMargin = marginBottom;

                adView.setLayoutParams(params);
            }
        });
    }
}
