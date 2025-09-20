package org.avium.systemui.lockscreen.util;

import android.os.SystemProperties;
import android.util.Log;

public class CustomLockscreenSettings {

    private static final String TAG = "AVIUM_LOCKSCREEN";

    private static final String PROP_ENABLED = "persist.avium.customlockscreen.enable";
    private static final String PROP_TYPE = "persist.avium.customlockscreen.type";
    private static final String PROP_COLOR = "persist.avium.customlockscreen.color";
    private static final String PROP_HOUR_COLOR = "persist.avium.customlockscreen.hour.color";
    private static final String PROP_MINUTE_COLOR = "persist.avium.customlockscreen.minute.color";

    public static boolean isEnabled() {
        boolean enabled = SystemProperties.getBoolean(PROP_ENABLED, false);
        Log.d(TAG, "Custom lockscreen enabled: " + enabled);
        return enabled;
    }

    public static int getClockType() {
        int type = SystemProperties.getInt(PROP_TYPE, 0);
        Log.d(TAG, "Clock type: " + type);
        return type;
    }

    @Deprecated
    public static String getClockColor() {
        String color = SystemProperties.get(PROP_COLOR, "white");
        Log.d(TAG, "Clock color (deprecated): " + color);
        return color;
    }

    public static String getHourColor() {
        String hourColor = SystemProperties.get(PROP_HOUR_COLOR, "");
        if (hourColor.isEmpty()) {
            hourColor = SystemProperties.get(PROP_COLOR, "white");
        }
        Log.d(TAG, "Hour color: " + hourColor);
        return hourColor;
    }

    public static String getMinuteColor() {
        String minuteColor = SystemProperties.get(PROP_MINUTE_COLOR, "");
        if (minuteColor.isEmpty()) {
            minuteColor = SystemProperties.get(PROP_COLOR, "white");
        }
        Log.d(TAG, "Minute color: " + minuteColor);
        return minuteColor;
    }

    public static boolean hasSeparateHourMinuteColors() {
        String hourColor = SystemProperties.get(PROP_HOUR_COLOR, "");
        String minuteColor = SystemProperties.get(PROP_MINUTE_COLOR, "");
        boolean hasSeparate = !hourColor.isEmpty() || !minuteColor.isEmpty();
        Log.d(TAG, "Has separate hour/minute colors: " + hasSeparate);
        return hasSeparate;
    }
}