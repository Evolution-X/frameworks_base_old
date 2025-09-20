package org.avium.systemui.lockscreen;

import android.content.Context;
import android.util.Log;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.type.smallcuteclock.SmallCuteClockController;
import org.avium.systemui.lockscreen.type.thinlongclock.ThinLongClockController;
import org.avium.systemui.lockscreen.type.moremorethin.MoreMoreThinClockController;
import org.avium.systemui.lockscreen.type.normaltime.NormalTimeClockController;
import org.avium.systemui.lockscreen.type.guoguoclock.GuoguoClockController2;
import org.avium.systemui.lockscreen.type.guoguoclock.GuoguoClockController3;
import org.avium.systemui.lockscreen.type.guoguoclock.GuoguoClockController4;
import org.avium.systemui.lockscreen.type.ntype.NTypeClockController;
import org.avium.systemui.lockscreen.type.ndot.NDotClockController;
import org.avium.systemui.lockscreen.type.graphic.GraphicClockController;
import org.avium.systemui.lockscreen.type.london_ug.LondonUGClockController;

/**
 * Factory for creating Avium custom lockscreen clocks
 *
 * Clock Type Mapping (internal factory types):
 * 2  -> SmallCute
 * 5  -> ThinLong
 * 6  -> MoreMoreThin
 * 8  -> NormalTime
 * 12 -> Guoguo2
 * 13 -> Guoguo3
 * 14 -> Guoguo4
 * 18 -> NType
 * 19 -> NDot
 * 20 -> Graphic
 * 22 -> LondonUG
 *
 * Note: These are mapped from clock_style settings values (18-27)
 * by KeyguardClockStyleSection.mapToAviumClockType()
 */
public class CustomLockScreenClockFactory {
    private static final String TAG = "AVIUM_LOCKSCREEN";

    /**
     * Creates a custom lockscreen clock based on the clock type from system properties
     *
     * @param context Android context
     * @return ICustomLockScreenClock instance or null if disabled/unknown
     */
    public static ICustomLockScreenClock create(Context context) {
        if (!CustomLockscreenSettings.isEnabled()) {
            Log.d(TAG, "Custom lockscreen is disabled");
            return null;
        }

        int clockType = CustomLockscreenSettings.getClockType();
        Log.d(TAG, "Creating clock for type: " + clockType);

        switch (clockType) {
            case 2:
                Log.d(TAG, "Creating SmallCuteClockController");
                return new SmallCuteClockController();
            case 5:
                Log.d(TAG, "Creating ThinLongClockController");
                return new ThinLongClockController();
            case 6:
                Log.d(TAG, "Creating MoreMoreThinClockController");
                return new MoreMoreThinClockController();
            case 8:
                Log.d(TAG, "Creating NormalTimeClockController");
                return new NormalTimeClockController();
            case 12:
                Log.d(TAG, "Creating GuoguoClockController2");
                return new GuoguoClockController2();
            case 13:
                Log.d(TAG, "Creating GuoguoClockController3");
                return new GuoguoClockController3();
            case 14:
                Log.d(TAG, "Creating GuoguoClockController4");
                return new GuoguoClockController4();
            case 18:
                Log.d(TAG, "Creating NTypeClockController");
                return new NTypeClockController(context);
            case 19:
                Log.d(TAG, "Creating NDotClockController");
                return new NDotClockController(context);
            case 20:
                Log.d(TAG, "Creating GraphicClockController");
                return new GraphicClockController(context);
            case 22:
                Log.d(TAG, "Creating LondonUGClockController");
                return new LondonUGClockController(context);
            default:
                Log.w(TAG, "Unknown clock type: " + clockType);
                return null;
        }
    }
}
