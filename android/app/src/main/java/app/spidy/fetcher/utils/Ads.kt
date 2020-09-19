package app.spidy.fetcher.utils

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.reward.RewardItem
import com.google.android.gms.ads.reward.RewardedVideoAd
import com.google.android.gms.ads.reward.RewardedVideoAdListener

object Ads {
//    const val APPLICATION_ID = "ca-app-pub-1517962596069817~7710237708"
    private const val INTERSTITIAL_ID = "ca-app-pub-1517962596069817/6228321997"
    private const val REWARDED_VIDEO_AD = "ca-app-pub-1517962596069817/4425162410"

    /* TEST ADS */
//    const val APPLICATION_ID = "ca-app-pub-3940256099942544~3347511713"
//    const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
//    const val REWARDED_VIDEO_AD = "ca-app-pub-3940256099942544/5224354917"

    private var interstitialAd: InterstitialAd? = null
    fun initInterstitial(context: Context) {
        interstitialAd = InterstitialAd(context)
        interstitialAd!!.adUnitId = INTERSTITIAL_ID
    }

    fun loadInterstitial() {
        interstitialAd?.loadAd(AdRequest.Builder().build())
    }

    fun showInterstitial() {
        interstitialAd?.show()
    }

    private var rewardedVideoAd: RewardedVideoAd? = null
    private var rewadedCallback: (() -> Unit)? = null
    var isRewarded = false
    private var isRewardLoaded = false

    fun initReward(context: Context) {
        rewardedVideoAd = MobileAds.getRewardedVideoAdInstance(context)
        rewardedVideoAd?.rewardedVideoAdListener = object : RewardedVideoAdListener {
            override fun onRewardedVideoAdClosed() {
                if (isRewarded) rewadedCallback?.invoke()
            }
            override fun onRewardedVideoAdLeftApplication() {}
            override fun onRewardedVideoAdLoaded() {
                isRewardLoaded = true
            }
            override fun onRewardedVideoAdOpened() {}
            override fun onRewardedVideoCompleted() {}
            override fun onRewarded(p0: RewardItem?) {
                isRewarded = true
            }
            override fun onRewardedVideoStarted() {}
            override fun onRewardedVideoAdFailedToLoad(p0: Int) {}
        }
    }

    fun loadReward() {
        rewardedVideoAd?.loadAd(REWARDED_VIDEO_AD, AdRequest.Builder().build())
    }

    fun showReward(onSuccess: () -> Unit) {
        if (isRewardLoaded) {
            isRewarded = false
//            rewadedCallback = onSuccess
            rewardedVideoAd?.show()
        } else {
            onSuccess.invoke()
        }
    }
}