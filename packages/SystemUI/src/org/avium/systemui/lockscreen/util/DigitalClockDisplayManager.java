package org.avium.systemui.lockscreen.util;

import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.util.Log;
import android.widget.ImageView;

public class DigitalClockDisplayManager {

    private static final String TAG = "AVIUM_LOCKSCREEN";

    private ImageView[] mDigitViews;
    private int[] mDigitResources;

    public DigitalClockDisplayManager(ImageView[] digitViews, int[] digitResources) {
        this.mDigitViews = digitViews;
        this.mDigitResources = digitResources;
    }

    public void updateTimeDisplay(String timeString) {
        if (mDigitViews == null || timeString == null) {
            return;
        }

        for (int i = 0; i < Math.min(mDigitViews.length, timeString.length()); i++) {
            if (mDigitViews[i] != null) {
                char digitChar = LockscreenClockUtils.getTimeDigitAt(timeString, i);
                int digitValue = LockscreenClockUtils.charDigitToInt(digitChar);
                if (digitValue < mDigitResources.length) {
                    mDigitViews[i].setImageResource(mDigitResources[digitValue]);
                }
            }
        }
    }

    public void applyColorAndEffects(ImageView[] hourViews, ImageView[] minuteViews) {
        String hourColorProp = CustomLockscreenSettings.getHourColor();
        String minuteColorProp = CustomLockscreenSettings.getMinuteColor();

        int hourColor = LockscreenClockUtils.parseColor(hourColorProp);
        int minuteColor = LockscreenClockUtils.parseColor(minuteColorProp);

        RenderEffect hourBlurEffect = null;
        RenderEffect minuteBlurEffect = null;

        if ("blur".equalsIgnoreCase(hourColorProp.trim())) {
            Log.d(TAG, "Applied blur effect to hour digits");
        }

        if ("blur".equalsIgnoreCase(minuteColorProp.trim())) {
            Log.d(TAG, "Applied blur effect to minute digits");
        }

        if (hourViews != null) {
            for (ImageView view : hourViews) {
                if (view != null) {
                    applyEffectToDigit(view, hourColor, hourBlurEffect);
                }
            }
        }

        if (minuteViews != null) {
            for (ImageView view : minuteViews) {
                if (view != null) {
                    applyEffectToDigit(view, minuteColor, minuteBlurEffect);
                }
            }
        }
    }

    private void applyEffectToDigit(ImageView iv, int color, RenderEffect effect) {
        if (effect != null) {
            iv.setColorFilter(Color.TRANSPARENT);
            iv.setRenderEffect(effect);
        } else {
            iv.setRenderEffect(null);
            iv.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }
}