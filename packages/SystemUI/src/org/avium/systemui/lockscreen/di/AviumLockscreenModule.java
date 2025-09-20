package org.avium.systemui.lockscreen.di;

import org.avium.systemui.lockscreen.CustomLockscreenClockManager;
import org.avium.systemui.lockscreen.NativeLockscreenViewHider;
import org.avium.systemui.lockscreen.util.SystemPropertiesWatcher;
import org.avium.systemui.lockscreen.CustomLockscreenRepository;

import dagger.Module;
import dagger.Provides;

import android.content.Context;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.util.settings.SecureSettings;

/**
 * Dagger module for Avium lockscreen components.
 * Note: CustomClockSection has been merged into KeyguardClockStyleSection in RisingOS.
 * This module now only provides the underlying managers and utilities.
 */
@Module
public class AviumLockscreenModule {
    
    @Provides
    @SysUISingleton
    public NativeLockscreenViewHider provideNativeLockscreenViewHider(Context context) {
        return new NativeLockscreenViewHider(context);
    }
    
    @Provides
    @SysUISingleton
    public CustomLockscreenClockManager provideCustomLockscreenClockManager(
            Context context, 
            NativeLockscreenViewHider nativeLockscreenViewHider,
            SystemPropertiesWatcher propertiesWatcher
    ) {
        return new CustomLockscreenClockManager(context, nativeLockscreenViewHider, propertiesWatcher);
    }
    
    @Provides
    @SysUISingleton
    public CustomLockscreenRepository provideCustomLockscreenRepository(Context context) {
        return new CustomLockscreenRepository(context);
    }
    
    @Provides
    @SysUISingleton
    public SystemPropertiesWatcher provideSystemPropertiesWatcher(Context context) {
        return new SystemPropertiesWatcher(context);
    }
}
