package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardedAdManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "RewardedAdManager"

        // Official Google AdMob Rewarded Video Test Ad Unit ID
        const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

        @Volatile
        private var INSTANCE: RewardedAdManager? = null

        fun getInstance(context: Context): RewardedAdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RewardedAdManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var isInitialized = false

    fun initialize(onInitialized: (() -> Unit)? = null) {
        if (isInitialized) {
            onInitialized?.invoke()
            return
        }
        MobileAds.initialize(appContext) {
            isInitialized = true
            Log.d(TAG, "Google Mobile Ads SDK initialized.")
            loadAd()
            onInitialized?.invoke()
        }
    }

    fun isAdReady(): Boolean = rewardedAd != null

    fun loadAd(onAdLoaded: (() -> Unit)? = null) {
        if (isLoading || rewardedAd != null) {
            if (rewardedAd != null) onAdLoaded?.invoke()
            return
        }

        isLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            appContext,
            TEST_REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    Log.d(TAG, "Rewarded video ad loaded successfully.")
                    onAdLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                    Log.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Shows the rewarded video ad to the user.
     * Compliant with AdMob policies: invokes onUserEarnedReward callback only when
     * video completion criteria is satisfied.
     */
    fun showAd(
        activity: Activity,
        onUserEarnedReward: (coinsAmount: Int) -> Unit,
        onAdDismissed: () -> Unit,
        onAdFailed: (String) -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            onAdFailed("Bonus video is still preparing. Please try again shortly.")
            loadAd()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded video ad dismissed.")
                rewardedAd = null
                loadAd() // Preload the next rewarded ad
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Rewarded video ad failed to show: ${adError.message}")
                rewardedAd = null
                loadAd()
                onAdFailed(adError.message)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded video ad shown to user.")
            }
        }

        ad.show(activity) { rewardItem ->
            val rewardAmount = if (rewardItem.amount > 0) rewardItem.amount else 10
            Log.d(TAG, "User earned reward: $rewardAmount coins.")
            onUserEarnedReward(rewardAmount)
        }
    }
}
