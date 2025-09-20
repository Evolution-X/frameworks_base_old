package org.avium.systemui.lockscreen;

import android.content.Context;
import android.view.View;

public interface ICustomLockScreenClock {

    View getView(Context context);

    void onTimeTick();

    void onNotificationStateChanged(boolean hasNotifications);

    void applyStyles();

    void onDestroy();
}