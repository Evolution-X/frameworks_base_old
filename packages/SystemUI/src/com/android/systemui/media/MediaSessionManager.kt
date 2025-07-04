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

import android.graphics.drawable.Drawable
import android.media.session.PlaybackState
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

class MediaSessionManager private constructor() {

    interface MediaDataListener {
        fun onPlaybackStateChanged(state: Int) {}
        fun onAlbumArtChanged(drawable: Drawable) {}
    }

    private val listeners = CopyOnWriteArrayList<WeakReference<MediaDataListener>>()

    fun addMediaDataListener(listener: MediaDataListener) {
        if (listeners.none { it.get() === listener }) {
            listeners.add(WeakReference(listener))
        }
    }

    fun removeMediaDataListener(listener: MediaDataListener) {
        listeners.removeAll { it.get() == null || it.get() === listener }
    }

    fun onPlaybackStateChanged(state: Int) {
        notifyListeners { it.onPlaybackStateChanged(state) }
    }

    fun onAlbumArtChanged(drawable: Drawable) {
        notifyListeners { it.onAlbumArtChanged(drawable) }
    }   

    private inline fun notifyListeners(action: (MediaDataListener) -> Unit) {
        val iterator = listeners.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            val listener = ref.get()
            if (listener != null) {
                action(listener)
            } else {
                iterator.remove()
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: MediaSessionManager? = null

        fun get(): MediaSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaSessionManager().also { INSTANCE = it }
            }
        }
    }
}
