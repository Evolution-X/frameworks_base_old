/*
 * Copyright (C) 2023-2024 The risingOS Android Project
 * Copyright (C) 2025 The RisingOS Revived Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.keyguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.os.UserHandle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.android.systemui.res.R
import com.android.systemui.util.MediaSessionManagerHelper
import com.android.systemui.util.settings.SystemSettings
import javax.inject.Inject

class NowBarHolder @JvmOverloads constructor(
    context: Context, 
    attrs: AttributeSet? = null, 
    defStyleAttr: Int = 0,
    private val systemSettings: SystemSettings? = null
) : ConstraintLayout(context, attrs, defStyleAttr), MediaSessionManagerHelper.MediaMetadataListener {

    private val TAG = "NowBarHolder"
    private var mViewPager: ViewPager? = null
    private var mMediaSessionManagerHelper: MediaSessionManagerHelper = MediaSessionManagerHelper.getInstance(context)
    
    private var isChargingStatusHandled = false
    private var isEnabled = false
    private var wasPlayingBefore = false
    private var lastMediaUpdateTime = 0L
    private val mediaCheckHandler = Handler(Looper.getMainLooper())
    
    // Runnable to check if media session has been abandoned
    private val mediaCheckRunnable = Runnable {
        val currentTime = System.currentTimeMillis()
        if (!mMediaSessionManagerHelper.isMediaPlaying() && 
            (currentTime - lastMediaUpdateTime > SESSION_TIMEOUT_MS)) {
            Log.d(TAG, "Media session appears to be abandoned, switching view")
            if (isChargingStatusHandled) {
                mViewPager?.setCurrentItem(1, true)
            } else {
                mViewPager?.setCurrentItem(0, true)
            }
            wasPlayingBefore = false
        } else {
            // Schedule another check if still in potential abandoned state
            if (!mMediaSessionManagerHelper.isMediaPlaying() && wasPlayingBefore) {
                startMediaCheckTask()
            }
        }
    }

    private val batteryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isEnabled) return
            
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    if (isCharging(intent) && isPluggedIn(intent) && !isChargingStatusHandled) {
                        mViewPager?.setCurrentItem(1, true)
                        isChargingStatusHandled = true
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    // Only switch away from battery view if no media is playing
                    if (!mMediaSessionManagerHelper.isMediaPlaying()) {
                        mViewPager?.setCurrentItem(0, true)
                    }
                    isChargingStatusHandled = false
                }
            }
        }
    }

    // Broadcast receiver for music player app states
    private val musicPlayerReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isEnabled) return
            
            when (intent.action) {
                "com.android.music.playerstatechanged", 
                "com.android.music.playstatechanged",
                "com.android.music.metachanged",
                "com.android.music.queuechanged", 
                "com.spotify.music.playbackstatechanged",
                "com.spotify.music.metadatachanged",
                "com.google.android.music.playstatechanged",
                "com.google.android.music.metachanged",
                "com.pandora.android.playbackstatuschanged",
                "com.telegram.player.closeplayback" -> {
                    Log.d(TAG, "Received music player broadcast: ${intent.action}")
                    // Give media session manager time to update
                    mediaCheckHandler.postDelayed({
                        handleMediaStateChange(true)
                    }, 200)
                }
            }
        }
    }

    companion object {
        private const val SESSION_TIMEOUT_MS = 1500L
    }

    init {
        inflate(context, R.layout.now_bar_holder, this)
        mViewPager = findViewById(R.id.nowBarViewPager)
        mViewPager?.adapter = NowBarAdapter(context)
        mViewPager?.setPageTransformer(false, PageTransitionTransformer())
        
        // Battery state receiver
        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_EXPORTED)
        
        updateVisibility()
    }

    private fun updateVisibility() {
        val resolver = context.contentResolver
        isEnabled = Settings.System.getIntForUser(
            resolver,
            "keyguard_now_bar_enabled",
            0,
            UserHandle.USER_CURRENT
        ) != 0
        
        visibility = if (isEnabled) View.VISIBLE else View.GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Music player state receiver
        val musicFilter = IntentFilter().apply {
            addAction("com.android.music.playerstatechanged")
            addAction("com.android.music.playstatechanged")
            addAction("com.android.music.metachanged")
            addAction("com.android.music.queuechanged")
            addAction("com.spotify.music.playbackstatechanged")
            addAction("com.spotify.music.metadatachanged")
            addAction("com.google.android.music.playstatechanged")
            addAction("com.google.android.music.metachanged")
            addAction("com.pandora.android.playbackstatuschanged")
            addAction("com.telegram.player.closeplayback")
        }
        context.registerReceiver(musicPlayerReceiver, musicFilter, Context.RECEIVER_EXPORTED)
        mMediaSessionManagerHelper.addMediaMetadataListener(this)
        updateVisibility()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(batteryReceiver)
            context.unregisterReceiver(musicPlayerReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        mMediaSessionManagerHelper.removeMediaMetadataListener(this)
        stopMediaCheckTask()
    }

    override fun onMediaMetadataChanged() {
        lastMediaUpdateTime = System.currentTimeMillis()
        handleMediaStateChange(false)
    }

    override fun onPlaybackStateChanged() {
        lastMediaUpdateTime = System.currentTimeMillis()
        handleMediaStateChange(false)
    }

    private fun handleMediaStateChange(forceCheck: Boolean) {
        if (!isEnabled) return
        
        val isPlaying = mMediaSessionManagerHelper.isMediaPlaying()
        
        Log.d(TAG, "Media state change: isPlaying=$isPlaying")
        
        if (isPlaying) {
            wasPlayingBefore = true
            mViewPager?.setCurrentItem(0, true)
            stopMediaCheckTask()
        } else if (wasPlayingBefore || forceCheck) {
            // If state changed from playing to not playing, or we're forced to check
            startMediaCheckTask()
        }
    }
    
    private fun startMediaCheckTask() {
        stopMediaCheckTask()
        mediaCheckHandler.postDelayed(mediaCheckRunnable, 500)
    }
    
    private fun stopMediaCheckTask() {
        mediaCheckHandler.removeCallbacks(mediaCheckRunnable)
    }

    private inner class NowBarAdapter(private val context: Context) : PagerAdapter() {

        override fun getCount(): Int {
            return 2
        }

        override fun instantiateItem(container: View, position: Int): Any {
            val view: View = when (position) {
                0 -> MusicNowBar(context)
                else -> BatteryNowBar(context)
            }
            (container as ViewPager).addView(view)
            return view
        }

        override fun destroyItem(container: View, position: Int, `object`: Any) {
            (container as ViewPager).removeView(`object` as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view == `object`
        }
    }

    private inner class PageTransitionTransformer : ViewPager.PageTransformer {

        private val SCALE_FACTOR = 0.9f
        private val TRANSLATION_Y_FACTOR = 40f
        private val ALPHA_FACTOR = 0.7f

        override fun transformPage(page: View, position: Float) {
            when {
                position < -1 || position > 1 -> page.alpha = 0f
                position <= 0 -> {
                    page.apply {
                        scaleX = 1f
                        scaleY = 1f
                        translationY = 0f
                        alpha = 1f
                    }
                }
                position <= 1 -> {
                    val scale = SCALE_FACTOR + (1 - SCALE_FACTOR) * (1 - position)
                    val translationY = position * TRANSLATION_Y_FACTOR
                    page.apply {
                        scaleX = scale
                        scaleY = scale
                        this.translationY = translationY
                        alpha = ALPHA_FACTOR + (1 - ALPHA_FACTOR) * (1 - position)
                    }
                }
            }
        }
    }

    private fun isCharging(intent: Intent): Boolean {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun isPluggedIn(intent: Intent): Boolean {
        val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        return chargePlug == BatteryManager.BATTERY_PLUGGED_AC
                || chargePlug == BatteryManager.BATTERY_PLUGGED_USB
                || chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS
    }
}
