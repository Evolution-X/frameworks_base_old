package org.avium.systemui.lockscreen;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.os.Process;
import javax.inject.Inject;
import com.android.systemui.dagger.SysUISingleton;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.util.SystemPropertiesWatcher;

@SysUISingleton
public class CustomLockscreenClockManager {

    private static final String TAG = "AVIUM_LOCKSCREEN";

    private final Context mContext;
    private ICustomLockScreenClock mCustomClock;
    private final NativeLockscreenViewHider mNativeViewHider;
    private final SystemPropertiesWatcher mPropertiesWatcher;
    private View mCustomClockView;

    @Inject
    public CustomLockscreenClockManager(Context context, NativeLockscreenViewHider nativeViewHider, SystemPropertiesWatcher propertiesWatcher) {
        this.mContext = context;
        this.mNativeViewHider = nativeViewHider;
        this.mPropertiesWatcher = propertiesWatcher;
    }

    public boolean isEnabled() {
        return CustomLockscreenSettings.isEnabled();
    }
    
    public void hideNativeClock(View view) {
        mNativeViewHider.hideNativeViews(view);
    }

    public View getView() {
        if (mCustomClockView == null && isEnabled()) {
            mCustomClock = CustomLockScreenClockFactory.create(mContext);
            if (mCustomClock != null) {
                mCustomClockView = mCustomClock.getView(mContext);
            }
        }
        return mCustomClockView;
    }

    public void onNotificationStateChanged(boolean hasNotifications) {
        if (mCustomClock != null && isEnabled()) {
            mCustomClock.onNotificationStateChanged(hasNotifications);
        }
    }

    public void onDestroy() {
        if (mCustomClock != null) {
            mCustomClock.onDestroy();
            mCustomClock = null;
            mCustomClockView = null; 
        }
        if (mPropertiesWatcher != null) {
            mPropertiesWatcher.destroy();
        }
    }

    public void restartSystemUI() {
        Process.killProcess(Process.myPid());
    }
}