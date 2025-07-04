/*
 * Copyright (C) 2025 The AxionAOSP Project
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
package com.android.systemui.media

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.android.internal.graphics.ColorUtils
import com.android.systemui.media.MediaScrimState.STATE_SCRIM_HIDDEN
import com.android.systemui.media.MediaScrimState.STATE_SCRIM_VISIBLE
import com.android.systemui.util.ScrimUtils
import kotlinx.coroutines.*
import kotlin.math.*

enum class MediaScrimState {
    STATE_SCRIM_VISIBLE,
    STATE_SCRIM_HIDDEN
}

class MediaViewController private constructor(
    private val context: Context = context.applicationContext
) : MediaSessionManager.MediaDataListener, ScrimUtils.ScrimEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var scrimState = STATE_SCRIM_HIDDEN
    
    private var listening = false
    private var featureEnabled = false
    private var artworkDrawable: Drawable? = null
    private var isMediaPlaying = false
    private var bouncerShowingOrKeyguardDismissing = false

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            updateSettings()
        }
    }

    private val mediaScrim = ImageView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private var mediaArtJob: Job? = null
    private var isAlbumArtVisible = false
    private val mediaFadeLevel = 40
    private var dismissingKeyguard = false

    init {        
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(LS_MEDIA_ART_ENABLED),
            false,
            settingsObserver
        )

        updateSettings()
    }

    private fun updateSettings() {
        featureEnabled = Settings.System.getIntForUser(
            context.contentResolver,
            LS_MEDIA_ART_ENABLED,
            0,
            UserHandle.USER_CURRENT
        ) == 1

        if (featureEnabled && !listening) {
            MediaSessionManager.get().addListener(this)
            ScrimUtils.get().addListener(this)
            listening = true
        } else if (!featureEnabled && listening) {
            MediaSessionManager.get().removeListener(this)
            ScrimUtils.get().removeListener(this)
            listening = false
        }
    }

    private suspend fun shouldShowMediaArt(): Boolean {
        val isPortrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val isKeyguard = ScrimUtils.get().isKeyguardShowing()
        val isDozing = ScrimUtils.get().isDozing()
        val isPanelFullyCollapsed = ScrimUtils.get().isPanelFullyCollapsed()

        val shouldShow = featureEnabled && !isDozing &&
            isPortrait && isKeyguard &&
            isMediaPlaying && isPanelFullyCollapsed &&
            !bouncerShowingOrKeyguardDismissing

        return shouldShow
    }

    private fun showMediaArt() {
        if (dismissingKeyguard) {
            return
        }
        updateMediaArt()
        mediaScrim.apply {
            alpha = 0f
            visibility = View.VISIBLE
            scrimState = STATE_SCRIM_VISIBLE
            animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null)
                .start()
        }
    }

    private fun updateMediaArt() {
        mediaArtJob?.cancel()
        mediaArtJob = coroutineScope.launch {
            processArtwork().let { drawable ->
                mediaScrim.setImageDrawable(drawable)
            }
        }
    }

    private suspend fun processArtwork(): LayerDrawable {
        val drawable = artworkDrawable ?: return LayerDrawable(arrayOf())

        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight

        val fadeColor = ColorUtils.blendARGB(
            Color.TRANSPARENT,
            Color.BLACK,
            mediaFadeLevel / 100f
        )
        val fadeOverlay = ColorDrawable(fadeColor)
        fadeOverlay.setBounds(0, 0, drawableWidth, drawableHeight)

        return LayerDrawable(arrayOf(drawable, fadeOverlay)).apply {
            setBounds(0, 0, drawableWidth, drawableHeight)
        }
    }

    fun cleanupResources() {
        if (dismissingKeyguard) return
        dismissingKeyguard = true
        mediaArtJob?.cancel()
        mediaScrim.animate()
            .alpha(0f)
            .setDuration(100)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mediaScrim.setImageDrawable(null)
                    mediaScrim.visibility = View.GONE
                    scrimState = STATE_SCRIM_HIDDEN
                    dismissingKeyguard = false
                }
            })
            .start()
    }

    override fun onAlbumArtChanged(drawable: Drawable) {
        coroutineScope.launch {
            artworkDrawable = drawable
            onMediaStateChanged()
            if (scrimState == STATE_SCRIM_VISIBLE) {
                updateMediaArt()
            }
        }
    }
    
    override fun onPlaybackStateChanged(state: Int) {
        isMediaPlaying = state == PlaybackState.STATE_PLAYING 
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }

    override fun onPrimaryBouncerShowingChanged(showing: Boolean) {
        bouncerShowingOrKeyguardDismissing = showing
        if (showing) {
            cleanupResources()
        }
        retry()
    }
    
    override fun onKeyguardGoingAwayChanged(goingAway: Boolean) {
        bouncerShowingOrKeyguardDismissing = goingAway
        if (goingAway) {
            cleanupResources()
        }
        retry()
    }
    
    override fun onKeyguardFadingAwayChanged(fadingAway: Boolean) {
        bouncerShowingOrKeyguardDismissing = fadingAway
        if (fadingAway) {
            cleanupResources()
        }
        retry()
    }

    override fun onDozingChanged() {
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }
    
    override fun onExpandedFractionChanged(expandedFraction: Float) {
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }
    
    override fun onBarStateChanged(state: Int) {
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }
    
    override fun onQsVisibilityChanged(visible: Boolean) {
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }

    override fun onKeyguardShowingChanged(showing: Boolean) {
        if (showing) {
            dismissingKeyguard = false
        } else {
            cleanupResources()
        }
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }
    
    fun retry() {
        mediaScrim.postDelayed({
            coroutineScope.launch {
                if (shouldShowMediaArt()) {
                    dismissingKeyguard = false
                    onMediaStateChanged()
                }
            }
        }, 500)
    }

    private suspend fun onMediaStateChanged() {
        val show = shouldShowMediaArt()
        if (show && scrimState == STATE_SCRIM_HIDDEN) {
            showMediaArt()
        } else if (!show && scrimState == STATE_SCRIM_VISIBLE){
            cleanupResources()
        }
    }

    fun getMediaArtScrim() = mediaScrim

    companion object {
        private const val LS_MEDIA_ART_ENABLED = "ls_media_art_enabled"
        private const val TAG = "MediaViewController"

        @Volatile private var INSTANCE: MediaViewController? = null
        @Volatile private var ctx: Context? = null

        fun init(context: Context) {
            ctx = context.applicationContext
        }

        fun get(): MediaViewController {
            val context = ctx ?: throw IllegalStateException("MediaViewController not initialized")
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaViewController(context).also { INSTANCE = it }
            }
        }
    }
}
