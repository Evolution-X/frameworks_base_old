/*
 * Copyright (C) 2024 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.android.systemui.usb

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.TetheringManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerExecutor
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import com.android.internal.app.AlertActivity
import com.android.internal.app.AlertController.AlertParams
import com.android.systemui.res.R
import com.android.systemui.broadcast.BroadcastDispatcher
import javax.inject.Inject

class UsbFunctionActivity @Inject constructor(
    private val broadcastDispatcher: BroadcastDispatcher,
): AlertActivity(), DialogInterface.OnClickListener {

    private lateinit var usbManager: UsbManager
    private lateinit var userManager: UserManager
    private lateinit var tetheringManager: TetheringManager

    private var tetheringSupported = false
    private var midiSupported = false
    private val uvcEnabled = UsbManager.isUvcSupportEnabled()

    private val handler = Handler(Looper.getMainLooper())
    private val executor = HandlerExecutor(handler)

    private var previousFunctions: Long = 0L
    private val tetheringCallback = object : TetheringManager.StartTetheringCallback {
        override fun onTetheringFailed(error: Int) {
            Log.w(TAG, "onTetheringFailed() error : $error")
            usbManager.setCurrentFunctions(previousFunctions)
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != UsbManager.ACTION_USB_STATE) {
                return
            }

            val connected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false)
            if (!connected) {
                dlog("usb disconnected, goodbye")
                finish()
            }
        }
    }

    private lateinit var adapter: UsbFunctionAdapter
    private lateinit var supportedFunctions: List<UsbFunction>

    override fun onCreate(savedInstanceState: Bundle?) {
        dlog("onCreate()")
        super.onCreate(savedInstanceState)

        window.addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)

        usbManager = getSystemService(UsbManager::class.java) ?: return
        userManager = getSystemService(UserManager::class.java) ?: return
        tetheringManager = getSystemService(TetheringManager::class.java) ?: return

        tetheringSupported = tetheringManager.isTetheringSupported()
        midiSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)

        supportedFunctions = getSupportedFunctions()
        adapter = UsbFunctionAdapter(this, supportedFunctions)

        mAlertParams.apply {
            mAdapter = adapter
            mOnClickListener = this@UsbFunctionActivity
            mTitle = getString(R.string.usb_use)
            mIsSingleChoice = true
            mCheckedItem = supportedFunctions.indexOf(getCurrentFunction())
            mPositiveButtonText = getString(com.android.internal.R.string.done_label)
            mPositiveButtonListener = this@UsbFunctionActivity
            mNeutralButtonText = getString(com.android.internal.R.string.more_item_label)
            mNeutralButtonListener = this@UsbFunctionActivity
        }
        dlog("mCheckedItem=${mAlertParams.mCheckedItem}")

        setupAlert()
        mAlert.listView?.requestFocus()

        broadcastDispatcher.registerReceiver(
            usbReceiver,
            IntentFilter(UsbManager.ACTION_USB_STATE)
        )
    }

    override fun onDestroy() {
        broadcastDispatcher.unregisterReceiver(usbReceiver)
        super.onDestroy()
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        dlog("onClick: which = $which")
        when (which) {
            AlertDialog.BUTTON_POSITIVE -> finish()
            AlertDialog.BUTTON_NEUTRAL -> {
                val intent = Intent()
                    .setClassName(
                        "com.android.settings",
                        "com.android.settings.Settings\$UsbDetailsActivity"
                    )
                runCatching {
                    startActivityAsUser(intent, UserHandle.CURRENT)
                    finish()
                }.onFailure { e ->
                    Log.e(TAG, "unable to start activity $intent" , e);
                }
            }
            else -> {
                adapter.getItem(which)?.let { setCurrentFunction(it) }
                // adapter.notifyDataSetChanged()
                finish()
            }
        }
    }

    private fun getSupportedFunctions() = ALL_FUNCTIONS
        .filter { function -> areFunctionsSupported(function.mask) }

    private fun getCurrentFunction(): UsbFunction {
        var currentFunctions = usbManager.getCurrentFunctions().also {
            Log.d(TAG, "current usb functions: $it (${UsbManager.usbFunctionsToString(it)})")
        }

        if ((currentFunctions and UsbManager.FUNCTION_ACCESSORY) != 0L) {
            currentFunctions = UsbManager.FUNCTION_MTP
        } else if (currentFunctions == UsbManager.FUNCTION_NCM) {
            currentFunctions = UsbManager.FUNCTION_RNDIS
        }

        return supportedFunctions
            .find { it -> it.mask == currentFunctions }
            ?: NONE_FUNCTION
    }

    private fun setCurrentFunction(function: UsbFunction) {
        if (isClickEventIgnored(function.mask)) {
            dlog("setCurrentFunction ignored for $function")
            return
        }

        dlog("setCurrentFunction: $function")
        when (function.mask) {
            UsbManager.FUNCTION_RNDIS -> {
                previousFunctions = usbManager.getCurrentFunctions()
                tetheringManager.startTethering(
                    TetheringManager.TETHERING_USB,
                    executor,
                    tetheringCallback
                )
            }
            else -> usbManager.setCurrentFunctions(function.mask)
        }
    }

    // Below functions are replicated from com.android.settings.connecteddevice.usb.UsbBackend

    private fun isClickEventIgnored(function: Long): Boolean {
        val currentFunctions = usbManager.getCurrentFunctions()
        return (currentFunctions and UsbManager.FUNCTION_ACCESSORY) != 0L
            && function == UsbManager.FUNCTION_MTP
    }

    private fun areFunctionsSupported(functions: Long): Boolean {
        if ((!midiSupported && (functions and UsbManager.FUNCTION_MIDI) != 0L)
            || (!tetheringSupported && (functions and UsbManager.FUNCTION_RNDIS) != 0L)) {
            return false
        }
        return !(areFunctionDisallowed(functions) || areFunctionsDisallowedBySystem(functions)
            || areFunctionsDisallowedByNonAdminUser(functions))
    }

    private fun isUsbFileTransferRestricted(): Boolean {
        return userManager.hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER)
    }

    private fun isUsbTetheringRestricted(): Boolean {
        return userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING)
    }

    private fun isUsbFileTransferRestrictedBySystem(): Boolean {
        return userManager.hasBaseUserRestriction(
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserHandle.of(UserHandle.myUserId())
        )
    }

    private fun isUsbTetheringRestrictedBySystem(): Boolean {
        return userManager.hasBaseUserRestriction(
            UserManager.DISALLOW_CONFIG_TETHERING,
            UserHandle.of(UserHandle.myUserId())
        )
    }

    private fun areFunctionDisallowed(functions: Long): Boolean {
        return (isUsbFileTransferRestricted() && ((functions and UsbManager.FUNCTION_MTP) != 0L
            || (functions and UsbManager.FUNCTION_PTP) != 0L))
            || (isUsbTetheringRestricted() && ((functions and UsbManager.FUNCTION_RNDIS) != 0L))
    }

    private fun areFunctionsDisallowedBySystem(functions: Long): Boolean {
        return (isUsbFileTransferRestrictedBySystem() && ((functions and UsbManager.FUNCTION_MTP) != 0L
            || (functions and UsbManager.FUNCTION_PTP) != 0L))
            || (isUsbTetheringRestrictedBySystem() && ((functions and UsbManager.FUNCTION_RNDIS) != 0L))
            || (!uvcEnabled && ((functions and UsbManager.FUNCTION_UVC) != 0L))
    }

    private fun areFunctionsDisallowedByNonAdminUser(functions: Long): Boolean {
        return !userManager.isAdminUser() && (functions and UsbManager.FUNCTION_RNDIS) != 0L
    }

    private companion object {
        const val TAG = "UsbFunctionActivity"

        val NONE_FUNCTION = UsbFunction(
            UsbManager.FUNCTION_NONE,
            "",
            R.string.usb_use_charging_only
        )

        val ALL_FUNCTIONS = listOf<UsbFunction>(
            UsbFunction(
                UsbManager.FUNCTION_MTP,
                UsbManager.USB_FUNCTION_MTP,
                R.string.usb_use_file_transfers
            ),
            UsbFunction(
                UsbManager.FUNCTION_RNDIS,
                UsbManager.USB_FUNCTION_RNDIS,
                R.string.usb_use_tethering
            ),
            UsbFunction(
                UsbManager.FUNCTION_MIDI,
                UsbManager.USB_FUNCTION_MIDI,
                R.string.usb_use_MIDI
            ),
            UsbFunction(
                UsbManager.FUNCTION_PTP,
                UsbManager.USB_FUNCTION_PTP,
                R.string.usb_use_photo_transfers
            ),
            UsbFunction(
                UsbManager.FUNCTION_UVC,
                UsbManager.USB_FUNCTION_UVC,
                R.string.usb_use_uvc_webcam
            ),
            NONE_FUNCTION
        )

        fun dlog(msg: String) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, msg)
            }
        }
    }
}

private data class UsbFunction(
    val mask: Long,
    val name: String,
    val descriptionResId: Int
)

private class UsbFunctionAdapter(
    private val context: Context,
    private val items: List<UsbFunction>,
) : ArrayAdapter<UsbFunction>(
    context,
    com.android.internal.R.layout.select_dialog_singlechoice_material,
    items
) {

    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(
            com.android.internal.R.layout.select_dialog_singlechoice_material,
            parent,
            false
        )

        val textView = view as CheckedTextView
        val function = getItem(position)
        textView.text = context.getString(
            function?.descriptionResId ?: com.android.internal.R.string.unknownName
        )

        // required for listview to trigger onclick
        view.focusable = View.NOT_FOCUSABLE
        view.setClickable(false)

        return view
    }

    private companion object {
        const val TAG = "UsbFunctionAdapter"

        fun dlog(msg: String) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, msg)
            }
        }
    }
}
