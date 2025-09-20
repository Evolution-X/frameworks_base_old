package org.avium.systemui.lockscreen

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.android.systemui.dagger.SysUISingleton
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings
import javax.inject.Inject

private const val TAG = "AVIUM_LOCKSCREEN"

@SysUISingleton
class NativeLockscreenViewHider @Inject constructor(private val context: Context) {

    fun hideNativeViews(entryView: View) {
        if (!CustomLockscreenSettings.isEnabled()) {
            Log.d(TAG, "Custom lockscreen is disabled, native views will remain visible.")
            return
        }
        entryView.postDelayed({
            val rootView = entryView.rootView as? ViewGroup ?: return@postDelayed
            val rootId = context.resources.getIdentifier("keyguard_root_view", "id", "com.android.systemui")
            if (rootView.id != rootId) {
                 Log.w(TAG, "Could not find keyguard_root_view.")
            }

            Log.d(TAG, "Hiding native lockscreen clock views...")
            listOf(
                "bc_smartspace_view",
                "date_smartspace_view",
                "lockscreen_clock_view",
                "lockscreen_clock_view_large",
                "keyguard_slice_view"
            ).map { resourceName ->
                val resourceId = context.resources.getIdentifier(resourceName, "id", "com.android.systemui")
                if (resourceId != 0) { 
                    rootView.findViewById<View?>(resourceId)
                } else {
                    Log.w(TAG, "Resource not found for name: $resourceName")
                    null
                }
            }.forEach { view ->
                view?.hideView()
            }
        }, 100) 
    }

    private fun View?.hideView() {
        if (this == null) return

        fun makeInvisible() {
            if (isVisible) {
                visibility = View.INVISIBLE
            }
        }

        fun makeSizeZero() {
            val params = layoutParams
            if (params != null) {
                if (params.height != 0) params.height = 0
                if (params.width != 0) params.width = 0
                layoutParams = params
            }
        }

        makeSizeZero()
        makeInvisible()

        viewTreeObserver?.takeIf { it.isAlive }?.addOnGlobalLayoutListener {
            makeSizeZero()
            makeInvisible()
        }
        viewTreeObserver?.takeIf { it.isAlive }?.addOnDrawListener {
            makeInvisible()
        }
        Log.d(TAG, "View ${this.id} hidden.")
    }
}