package org.avium.systemui.lockscreen.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import android.os.SystemProperties;
import android.util.Log;
import javax.inject.Inject;
import com.android.systemui.dagger.SysUISingleton;

@SysUISingleton
public class SystemPropertiesWatcher {

    private static final String TAG = "AVIUM_LOCKSCREEN";
    private static final String ACTION_SETTINGS_CHANGED = "org.avium.systemui.lockscreen.SETTINGS_CHANGED";
    
    private static final String[] WATCHED_PROPERTIES = {
        "persist.avium.customlockscreen.enable",
        "persist.avium.customlockscreen.type", 
        "persist.avium.customlockscreen.color",
        "persist.avium.customlockscreen.hour.color",
        "persist.avium.customlockscreen.minute.color"
    };

    private final Context mContext;
    private final BroadcastReceiver mSettingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SETTINGS_CHANGED.equals(intent.getAction())) {
                Log.d(TAG, "Received settings changed broadcast, restarting SystemUI");
                Process.killProcess(Process.myPid());
            }
        }
    };

    @Inject
    public SystemPropertiesWatcher(Context context) {
        mContext = context;
        registerReceiver();
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_SETTINGS_CHANGED);
        mContext.registerReceiver(mSettingsReceiver, filter, Context.RECEIVER_EXPORTED);
        Log.d(TAG, "SystemPropertiesWatcher registered for action: " + ACTION_SETTINGS_CHANGED);
    }

    public void destroy() {
        try {
            mContext.unregisterReceiver(mSettingsReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver not registered", e);
        }
    }
}