/*
 * Copyright (C) 2019-2024 TenX-OS
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

package com.android.systemui.tenx.batterybar;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.systemui.Dependency;
import com.android.systemui.tuner.TunerService;

public class BatteryBar extends RelativeLayout implements Animatable, TunerService.Tunable {

    private static final String TAG = BatteryBar.class.getSimpleName();

    // Total animation duration
    private static final int ANIM_DURATION = 1000; // 5 seconds

    // When to use the low battery color
    private static final int BATTERY_LOW_VALUE = 20;

    private boolean mAttached = false;
    private int mBatteryLevel = 0;
    private int mChargingLevel = -1;
    private boolean mBatteryCharging = false;
    private boolean shouldAnimateCharging = true;
    private boolean isAnimating = false;

    int mLocation;
    private int mColor;
    private int mChargingColor;
    private int mBatteryLowColorWarning;
    private int mLowColor = 0xFFFF4400;
    private int mHighColor = 0xFF99CC00;
    private boolean mUseChargingColor;
    private boolean mBlendColor;
    private boolean mBlendColorReversed;
    private boolean mUseGradientColor;

    LinearLayout mBatteryBarLayout;
    View mBatteryBar;

    LinearLayout mChargerLayout;
    View mCharger;

    GradientDrawable mBarGradient;
    int[] mGradientColors;

    public static final int STYLE_REGULAR = 0;
    public static final int STYLE_SYMMETRIC = 1;

    boolean vertical = false;

    private static final String STATUSBAR_BATTERY_BAR =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR;
    private static final String STATUSBAR_BATTERY_BAR_COLOR =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR_COLOR;
    private static final String STATUSBAR_BATTERY_BAR_CHARGING_COLOR =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR_CHARGING_COLOR;
    private static final String STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR_WARNING =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR_WARNING;
    private static final String STATUSBAR_BATTERY_BAR_ANIMATE =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR_ANIMATE;
    private static final String STATUSBAR_BATTERY_BAR_ENABLE_CHARGING_COLOR =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR_ENABLE_CHARGING_COLOR;
    private static final String STATUSBAR_BATTERY_BAR_BLEND_COLOR =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR_BLEND_COLOR;
    private static final String STATUSBAR_BATTERY_BAR_BLEND_COLOR_REVERSE =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR_BLEND_COLOR_REVERSE;
    private static final String STATUSBAR_BATTERY_BAR_HIGH_COLOR =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR_HIGH_COLOR;
    private static final String STATUSBAR_BATTERY_BAR_LOW_COLOR =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR_LOW_COLOR;
    private static final String STATUSBAR_BATTERY_BAR_USE_GRADIENT_COLOR =
            "system:" + Settings.System.STATUSBAR_BATTERY_BAR_USE_GRADIENT_COLOR;

    public BatteryBar(Context context) {
        this(context, null);
    }

    public BatteryBar(Context context, boolean isCharging, int currentCharge) {
        this(context, null);

        mBatteryLevel = currentCharge;
        mBatteryCharging = isCharging;
    }

    public BatteryBar(Context context, boolean isCharging, int currentCharge, boolean isVertical) {
        this(context, null);

        mBatteryLevel = currentCharge;
        mBatteryCharging = isCharging;
        vertical = isVertical;
    }

    public BatteryBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources res = getResources();

        mGradientColors = new int[3];
        mGradientColors[0] = mLowColor;
        mGradientColors[1] = mHighColor;
        mGradientColors[2] = mHighColor;

        mBarGradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, mGradientColors);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mAttached)
            return;

        mAttached = true;

        mBatteryBarLayout = new LinearLayout(mContext);
        addView(mBatteryBarLayout, new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        mBatteryBar = new View(mContext);
        mBatteryBarLayout.addView(mBatteryBar, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        float dp = 4f;
        int pixels = (int) (metrics.density * dp + 0.5f);

        // charger
        mChargerLayout = new LinearLayout(mContext);

        if (vertical)
            addView(mChargerLayout, new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    pixels));
        else
            addView(mChargerLayout, new RelativeLayout.LayoutParams(pixels,
                    LayoutParams.MATCH_PARENT));

        mCharger = new View(mContext);
        mChargerLayout.setVisibility(View.GONE);
        mChargerLayout.addView(mCharger, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());

        Dependency.get(TunerService.class).addTunable(this,
                STATUSBAR_BATTERY_BAR,
                STATUSBAR_BATTERY_BAR_COLOR,
                STATUSBAR_BATTERY_BAR_CHARGING_COLOR,
                STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR_WARNING,
                STATUSBAR_BATTERY_BAR_ANIMATE,
                STATUSBAR_BATTERY_BAR_ENABLE_CHARGING_COLOR,
                STATUSBAR_BATTERY_BAR_BLEND_COLOR,
                STATUSBAR_BATTERY_BAR_BLEND_COLOR_REVERSE,
                STATUSBAR_BATTERY_BAR_LOW_COLOR,
                STATUSBAR_BATTERY_BAR_HIGH_COLOR,
                STATUSBAR_BATTERY_BAR_USE_GRADIENT_COLOR);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (!mAttached)
            return;

        mAttached = false;

        Dependency.get(TunerService.class).removeTunable(this);
        getContext().unregisterReceiver(mIntentReceiver);
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mBatteryCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_CHARGING;
                if (mBatteryCharging && mBatteryLevel < 100) {
                    start();
                } else {
                    stop();
                }
                setProgress(mBatteryLevel);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stop();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mBatteryCharging && mBatteryLevel < 100) {
                    start();
                }
            }
        }
    };

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case STATUSBAR_BATTERY_BAR:
                mLocation =
                        TunerService.parseInteger(newValue, 0);
                break;
            case STATUSBAR_BATTERY_BAR_COLOR:
                mColor =
                        TunerService.parseInteger(newValue, 0xff76c124);
                break;
            case STATUSBAR_BATTERY_BAR_CHARGING_COLOR:
                mChargingColor =
                        TunerService.parseInteger(newValue, 0xffffc90f);
                break;
            case STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR_WARNING:
                mBatteryLowColorWarning =
                        TunerService.parseInteger(newValue, 0xfff90028);
                break;
            case STATUSBAR_BATTERY_BAR_ANIMATE:
                shouldAnimateCharging =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            case STATUSBAR_BATTERY_BAR_ENABLE_CHARGING_COLOR:
                mUseChargingColor =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            case STATUSBAR_BATTERY_BAR_BLEND_COLOR:
                mBlendColor =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            case STATUSBAR_BATTERY_BAR_BLEND_COLOR_REVERSE:
                mBlendColorReversed =
                        TunerService.parseIntegerSwitch(newValue, false);
                break;
            case STATUSBAR_BATTERY_BAR_USE_GRADIENT_COLOR:
                mUseGradientColor =
                    TunerService.parseIntegerSwitch(newValue, false);
                break;
            case STATUSBAR_BATTERY_BAR_LOW_COLOR:
                mLowColor =
                    TunerService.parseInteger(newValue, 0xffff4400);
                break;
            case STATUSBAR_BATTERY_BAR_HIGH_COLOR:
                mHighColor =
                    TunerService.parseInteger(newValue, 0xff99CC00);
                break;
            default:
                break;
        }
        if (mLocation > 0 && shouldAnimateCharging && mBatteryCharging && mBatteryLevel < 100) {
            start();
        } else {
            stop();
        }
        setProgress(mBatteryLevel);
    }

    private void setProgress(int n) {
        if (vertical) {
            int w = (int) (((getHeight() / 100.0) * n) + 0.5);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBatteryBarLayout
                    .getLayoutParams();
            params.height = w;
            mBatteryBarLayout.setLayoutParams(params);

        } else {
            int w = (int) (((getWidth() / 100.0) * n) + 0.5);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBatteryBarLayout
                    .getLayoutParams();
            params.width = w;
            mBatteryBarLayout.setLayoutParams(params);
        }
        // Update color
        int color = getColorForPercent(n);

        if (mUseGradientColor) {
            float size = n / 100f;
            float size_ = n / 150f;
            mGradientColors[0] = mLowColor;
            mGradientColors[1] = mixColors(mHighColor, mLowColor, size_);
            mGradientColors[2] = mixColors(mHighColor, mLowColor, size);
            mBarGradient.setColors(mGradientColors);
            mBatteryBar.setBackgroundDrawable(mBarGradient);
        } else {
            mBatteryBar.setBackgroundColor(color);
        }
        mCharger.setBackgroundColor(color);
    }

    private int mixColors(int color1, int color2, float mix) {
        int[] rgb1 = colorToRgb(color1);
        int[] rgb2 = colorToRgb(color2);

        rgb1[0] = mixedValue(rgb1[0], rgb2[0], mix);
        rgb1[1] = mixedValue(rgb1[1], rgb2[1], mix);
        rgb1[2] = mixedValue(rgb1[2], rgb2[2], mix);
        rgb1[3] = mixedValue(rgb1[3], rgb2[3], mix);

        return rgbToColor(rgb1);
    }

    private int[] colorToRgb(int color) {
        int[] rgb = {(color & 0xFF000000) >> 24, (color & 0xFF0000) >> 16, (color & 0xFF00) >> 8, (color & 0xFF)};
        return rgb;
    }

    private int rgbToColor(int[] rgb) {
        return (rgb[0] << 24) + (rgb[1] << 16) + (rgb[2] << 8) + rgb[3];
    }

    private int mixedValue(int val1, int val2, float mix) {
        return (int)Math.min((mix * val1 + (1f - mix) * val2), 255f);
    }

    @Override
    public void start() {
        if (!shouldAnimateCharging)
            return;

        if (vertical) {
            TranslateAnimation a = new TranslateAnimation(getX(), getX(), getHeight(),
                    mBatteryBarLayout.getHeight());
            a.setInterpolator(new AccelerateInterpolator());
            a.setDuration(ANIM_DURATION);
            a.setRepeatCount(Animation.INFINITE);
            mChargerLayout.startAnimation(a);
            mChargerLayout.setVisibility(View.VISIBLE);
        } else {
            TranslateAnimation a = new TranslateAnimation(getWidth(), mBatteryBarLayout.getWidth(),
                    getTop(), getTop());
            a.setInterpolator(new AccelerateInterpolator());
            a.setDuration(ANIM_DURATION);
            a.setRepeatCount(Animation.INFINITE);
            mChargerLayout.startAnimation(a);
            mChargerLayout.setVisibility(View.VISIBLE);
        }
        isAnimating = true;
    }

    @Override
    public void stop() {
        mChargerLayout.clearAnimation();
        mChargerLayout.setVisibility(View.GONE);
        isAnimating = false;
    }

    @Override
    public boolean isRunning() {
        return isAnimating;
    }

    private static int getBlendColorForPercent(int fullColor, int emptyColor, boolean reversed,
                                        int percentage) {
        float[] newColor = new float[3];
        float[] empty = new float[3];
        float[] full = new float[3];
        Color.colorToHSV(fullColor, full);
        int fullAlpha = Color.alpha(fullColor);
        Color.colorToHSV(emptyColor, empty);
        int emptyAlpha = Color.alpha(emptyColor);
        float blendFactor = percentage/100f;
        if (reversed) {
            if (empty[0] < full[0]) {
                empty[0] += 360f;
            }
            newColor[0] = empty[0] - (empty[0]-full[0])*blendFactor;
        } else {
            if (empty[0] > full[0]) {
                full[0] += 360f;
            }
            newColor[0] = empty[0] + (full[0]-empty[0])*blendFactor;
        }
        if (newColor[0] > 360f) {
            newColor[0] -= 360f;
        } else if (newColor[0] < 0) {
            newColor[0] += 360f;
        }
        newColor[1] = empty[1] + ((full[1]-empty[1])*blendFactor);
        newColor[2] = empty[2] + ((full[2]-empty[2])*blendFactor);
        int newAlpha = (int) (emptyAlpha + ((fullAlpha-emptyAlpha)*blendFactor));
        return Color.HSVToColor(newAlpha, newColor);
    }

    private int getColorForPercent(int percentage) {
        if (mBatteryCharging && mUseChargingColor) {
            return mChargingColor;
        } else if (mBlendColor) {
            return getBlendColorForPercent(mColor, mBatteryLowColorWarning,
                    mBlendColorReversed, percentage);
        } else {
            return percentage > BATTERY_LOW_VALUE ? mColor : mBatteryLowColorWarning;
        }
    }
}
