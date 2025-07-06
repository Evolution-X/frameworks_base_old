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
package com.android.systemui.util

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

class WeakListenerManager<T> {

    private val listeners = ConcurrentLinkedQueue<WeakReference<T>>()

    fun addListener(listener: T) {
        for (ref in listeners) {
            if (ref.get() === listener) return
        }
        listeners.add(WeakReference(listener))
    }

    fun removeListener(listener: T) {
        val iterator = listeners.iterator()
        while (iterator.hasNext()) {
            val l = iterator.next().get()
            if (l == null || l === listener) {
                iterator.remove()
            }
        }
    }

    fun notify(action: (T) -> Unit) {
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

    @JvmOverloads
    fun notifyConsumer(action: Consumer<T>) {
        notify { action.accept(it) }
    }
}
