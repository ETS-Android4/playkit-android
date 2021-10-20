package com.kaltura.playkit.ads

import androidx.annotation.Nullable
import com.kaltura.playkit.MessageBus
import com.kaltura.playkit.PKLog
import com.kaltura.playkit.Player
import com.kaltura.playkit.PlayerEvent
import com.kaltura.playkit.plugins.ads.AdEvent
import java.util.*

class PKAdvertisingController: PKAdvertising {

    private val log = PKLog.get(PKAdvertisingController::class.java.simpleName)
    private var player: Player? = null
    private var messageBus: MessageBus? = null
    private var adController: AdController? = null
    private var advertising: Advertising? = null
    private var advertisingTree: AdvertisingTree? = null
    private var cuePointsList: LinkedList<Long>? = null
    private var adsConfigMap: MutableMap<Long, AdPodConfig?>? = null

    private val DEFAULT_AD_INDEX: Int = Int.MIN_VALUE
    private val PREROLL_AD_INDEX: Int = 0
    private var POSTROLL_AD_INDEX: Int = 0

    private var currentAdIndexPosition: Int = DEFAULT_AD_INDEX
    private var nextAdIndexForMonitoring: Int = DEFAULT_AD_INDEX
    private var adPlaybackTriggered: Boolean = false

    override fun playAdNow() {
        if (hasPostRoll()) {
            POSTROLL_AD_INDEX = cuePointsList?.size?.minus(1)!!
        }

        if (hasPreRoll()) {
            val preRollAdUrl = getAdFromAdConfigMap(PREROLL_AD_INDEX)
            if (preRollAdUrl != null) {
                adController?.playAdNow(preRollAdUrl)
            }
        } else if (midRollAdsCount() > 0){
            cuePointsList?.let {
                if (it.isNotEmpty()) {
                    // Update next Ad index for monitoring
                    nextAdIndexForMonitoring = 0
                }
            }
        }
    }

    override fun playAdNow(vastAdTag: String) {
        TODO("Not yet implemented")
    }

    override fun getCurrentAd() {
        TODO("Not yet implemented")
    }

    fun setPlayer(player: Player, messageBus: MessageBus) {
        this.player = player
        this.messageBus = messageBus
        subscribeToAdEvents()
    }

    fun setAdController(adController: AdController) {
        this.adController = adController
    }

    fun setAdvertising(advertising: Advertising) {
        this.advertising = advertising
        adController?.advertisingConfigured(true)
        advertisingTree = AdvertisingTree(advertising)
        cuePointsList = advertisingTree?.getCuePointsQueue()
        adsConfigMap = advertisingTree?.getAdsConfigMap()
    }

    //  15230  15800/ 1000 => 15 * 1000 => 15000 // TOD0: Check what will happen if app passed 15100 and 15500

    private fun subscribeToAdEvents() {
        player?.addListener(this, PlayerEvent.playheadUpdated) { event ->
            log.d("playheadUpdated ${event.position} & nextAdIndexForMonitoring is $nextAdIndexForMonitoring & nextAdForMonitoring ad position is = ${cuePointsList?.get(nextAdIndexForMonitoring)}")
            cuePointsList?.let { list ->
                if (event.position >= list[nextAdIndexForMonitoring] && list[nextAdIndexForMonitoring] != list.last && !adPlaybackTriggered) {
                    log.d("nextAdForMonitoring ad position is = $list[nextAdIndexForMonitoring]")
                    // TODO: handle situation of player.pause or content_pause_requested
                    // (because there is a delay while loading the ad
                    adPlaybackTriggered = true
                    getAdFromAdConfigMap(nextAdIndexForMonitoring)?.let { adUrl ->
                        adController?.playAdNow(adUrl)
                    }
                }
            }
        }

        player?.addListener(this, PlayerEvent.seeked) {
            log.d("Player seeked for position = ${player?.currentPosition}" )
            if (midRollAdsCount() > 0) {
                val lastAdPosition = getImmediateLastAdPosition(player?.currentPosition)
                if (lastAdPosition > 0) {
                    adPlaybackTriggered = true
                    getAdFromAdConfigMap(lastAdPosition)?.let { adUrl ->
                        adController?.playAdNow(adUrl)
                    }
                }
            }
        }

        player?.addListener(this, PlayerEvent.ended) {
            if (hasPostRoll()) {
                getAdFromAdConfigMap(POSTROLL_AD_INDEX)?.let {
                    adController?.playAdNow(it)
                }
            } else {
                currentAdIndexPosition = DEFAULT_AD_INDEX
                nextAdIndexForMonitoring = DEFAULT_AD_INDEX
                adPlaybackTriggered = false
            }
        }

        player?.addListener(this, AdEvent.started) {
            log.d("started")
        }

        player?.addListener(this, AdEvent.contentResumeRequested) {
            log.d("contentResumeRequested ${player?.currentPosition}")
//            player?.currentPosition?.let { currentPosition ->
//                if (currentPosition >= 0L) {
//                    cuePointsList?.peek()?.let {
//                        nextAdIndexForMonitoring = it
//                        adPlaybackTriggered = false
//                    }
//                }
//            }
        }

        player?.addListener(this, AdEvent.contentPauseRequested) {
            log.d("contentPauseRequested")
        }

        player?.addListener(this, AdEvent.adPlaybackInfoUpdated) {
            log.d("adPlaybackInfoUpdated")
        }

        player?.addListener(this, AdEvent.skippableStateChanged) {
            log.d("skippableStateChanged")
        }

        player?.addListener(this, AdEvent.adRequested) {
            log.d("adRequested")
        }

        player?.addListener(this, AdEvent.playHeadChanged) {
            log.d("playHeadChanged")
        }

        player?.addListener(this, AdEvent.adBreakStarted) {
            log.d("adBreakStarted")
        }

        player?.addListener(this, AdEvent.cuepointsChanged) {
            log.d("cuepointsChanged")
        }

        player?.addListener(this, AdEvent.loaded) {
            log.d("loaded")
        }

        player?.addListener(this, AdEvent.resumed) {
            log.d("resumed")
        }

        player?.addListener(this, AdEvent.paused) {
            log.d("paused")
        }

        player?.addListener(this, AdEvent.skipped) {
            log.d("skipped")
        }

        player?.addListener(this, AdEvent.allAdsCompleted) {
            log.d("allAdsCompleted")
        }

        player?.addListener(this, AdEvent.completed) {
            //  isCustomAdTriggered = false
            log.d("completed")
            adPlaybackTriggered = false
            changeAdPodState(AdState.PLAYED)
        }

        player?.addListener(this, AdEvent.firstQuartile) {
            log.d("firstQuartile")
        }

        player?.addListener(this, AdEvent.midpoint) {
            log.d("midpoint")
        }

        player?.addListener(this, AdEvent.thirdQuartile) {
            log.d("thirdQuartile")
        }

        player?.addListener(this, AdEvent.adBreakEnded) {
            log.d("adBreakEnded")
        }

        player?.addListener(this, AdEvent.adClickedEvent) {
            log.d("adClickedEvent")
        }

        player?.addListener(this, AdEvent.error) {
            log.d("AdEvent.error ${it}")
            adPlaybackTriggered = false
            val ad = getAdFromAdConfigMap(currentAdIndexPosition)
            if (ad.isNullOrEmpty()) {
                log.d("Ad is completely errored $it")
                changeAdPodState(AdState.ERROR)
            } else {
                log.d("Playing next waterfalling ad")
                adController?.playAdNow(ad)
            }
        }
    }

    private fun getAdFromAdConfigMap(adIndex: Int): String? {
        var adUrl: String? = null
        cuePointsList?.let { queue ->
            if (queue.isNotEmpty()) {
                val adPosition: Long = queue[adIndex]
                adsConfigMap?.let { adsMap ->
                    getAdPodConfigMap(adPosition)?.let {
                        adUrl = fetchPlayableAdFromAdsList(it)
                        adUrl?.let {
                            currentAdIndexPosition = adIndex
                        }
                    }
                }
            }
        }

        return adUrl
    }

    @Nullable
    private fun getAdPodConfigMap(position: Long?): AdPodConfig? {
        var adPodConfig: AdPodConfig? = null
        advertisingTree?.let { _ ->
            adsConfigMap?.let { adsMap ->
                if (adsMap.contains(position)) {
                    adPodConfig = adsMap[position]
                }
            }
        }

        log.d("getAdPodConfigMap AdPodConfig is $adPodConfig and podState is ${adPodConfig?.adPodState}")
        return adPodConfig
    }

    private fun fetchPlayableAdFromAdsList(adPodConfig: AdPodConfig?): String? {
        log.d("fetchPlayableAdFromAdsList AdPodConfig position is ${adPodConfig?.adPosition}")
        var adTagUrl: String? = null

        when (adPodConfig?.adPodState) {
            AdState.READY -> {
                log.d("I am in ready State and getting the first ad Tag.")
                adPodConfig.adList?.let { adUrlList ->
                    adPodConfig.adPodState = AdState.PLAYING
                    if (adUrlList.isNotEmpty()) {
                        adTagUrl = adUrlList[0].ad
                        adUrlList[0].adState = AdState.PLAYING
                    }
                }
            }

            AdState.PLAYING -> {
                log.d("I am in Playing State and checking for the next ad Tag.")
                adPodConfig.adList?.let { adUrlList ->
                    for (specificAd: Ad in adUrlList) {
                        if (specificAd.adState == AdState.PLAYING) {
                            specificAd.adState = AdState.ERROR
                        } else {
                            adTagUrl = specificAd.ad
                            specificAd.adState = AdState.PLAYING
                            break
                        }
                    }
                }
            }
        }

        return adTagUrl
    }

    private fun changeAdPodState(adState: AdState) {
        log.d("changeAdPodState AdState is $adState")
        advertisingTree?.let { _ ->
            cuePointsList?.let { queue ->
                if (queue.isNotEmpty()) {
                    adsConfigMap?.let { adsMap ->
                        if (currentAdIndexPosition != DEFAULT_AD_INDEX) {
                            val adPosition: Long = queue[currentAdIndexPosition]
                            val adPodConfig: AdPodConfig? = adsMap[adPosition]
                            adPodConfig?.let { adPod ->
                                log.d("AdState is changed for AdPod position ${adPod.adPosition}")
                                adPod.adPodState = adState
                               // queue.remove(adPosition)
                               // currentAdIndexPosition = DEFAULT_AD_INDEX
                                if (currentAdIndexPosition < queue.size - 1) {
                                    // Update next Ad index for monitoring
                                    nextAdIndexForMonitoring = currentAdIndexPosition + 1
                                    log.d("nextAdIndexForMonitoring is ${nextAdIndexForMonitoring}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Check is preRoll ad is present
     */
    private fun hasPreRoll(): Boolean {
        return cuePointsList?.first == 0L
    }

    /**
     * Check is postRoll ad is present
     */
    private fun hasPostRoll(): Boolean {
        return cuePointsList?.last == -1L
    }

    /**
     * Get the number of midRolls,
     * if no midRoll is present count will be zero.
     */
    private fun midRollAdsCount(): Int {
        cuePointsList?.let {
            return if (hasPreRoll() && hasPostRoll()) {
                it.size.minus(2)
            } else if (hasPreRoll() || hasPostRoll()) {
                it.size.minus(1)
            } else {
                it.size
            }
        }
        return 0
    }

    private fun getImmediateLastAdPosition(seekPosition: Long?): Int {
        if (seekPosition == null || cuePointsList == null || cuePointsList.isNullOrEmpty()) {
            log.d("Error in getImmediateLastAdPosition returning DEFAULT_AD_POSITION")
            return DEFAULT_AD_INDEX
        }

        var adPosition = -1

        cuePointsList?.let {
            if (seekPosition > 0 && it.isNotEmpty() && it.size > 1) {
                var lowerIndex: Int = if (it.first == 0L) 1 else 0
                var upperIndex: Int = if (it.last == -1L) it.size -2 else (it.size - 1)

                while (lowerIndex <= upperIndex) {
                    val midIndex = lowerIndex + (upperIndex - lowerIndex) / 2

                    if (it[midIndex] == seekPosition) {
                        adPosition = midIndex
                        break
                    } else if (it[midIndex] < seekPosition) {
                        adPosition = midIndex
                        lowerIndex = midIndex + 1
                    } else if (it[midIndex] > seekPosition) {
                        upperIndex = midIndex - 1
                    }
                }
            }
        }

        log.d("Immediate last Ad Position ${adPosition}")
        return adPosition
    }
}