package org.avium.systemui.lockscreen.type.normaltime;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.android.systemui.res.R;
import org.avium.systemui.lockscreen.util.BaseLockscreenController;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.util.DigitalClockDisplayManager;
import org.avium.systemui.lockscreen.util.GlassClockManager;
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;
import org.avium.systemui.lockscreen.util.LockscreenLayoutManager;

import java.util.Locale;

public class NormalTimeClockController extends BaseLockscreenController {

    private static final int DIGIT_WIDTH_DP = 55;
    private static final int DIGIT_HEIGHT_DP = 84;
    private static final int COLON_WIDTH_DP = 27;
    private static final int COLON_HEIGHT_DP = 84;

    private ImageView mHour1, mHour2, mColon, mMinute1, mMinute2;
    private DigitalClockDisplayManager mDigitalClockDisplayManager;

    private boolean mUseBlurEffect;
    private GlassClockManager mGlassClockManager;

    private final int[] mDigitResources = new int[]{
        R.drawable.normaltime_0, R.drawable.normaltime_1, R.drawable.normaltime_2,
        R.drawable.normaltime_3, R.drawable.normaltime_4, R.drawable.normaltime_5,
        R.drawable.normaltime_6, R.drawable.normaltime_7, R.drawable.normaltime_8,
        R.drawable.normaltime_9
    };

    @Override
    public View getView(Context context) {
        mContext = context;
        mUseBlurEffect = "blur".equalsIgnoreCase(CustomLockscreenSettings.getClockColor().trim());

        createViews();
        setupLayout();

        if (mUseBlurEffect) {
            mGlassClockManager.prepareWallpaper();
        }

        initializeCommonViews();
        return mContainer;
    }

    private void createViews() {
        mContainer = new ConstraintLayout(mContext);
        mContainer.setId(View.generateViewId());

        if (mUseBlurEffect) {
            mGlassClockManager = new GlassClockManager(mContext, 4, mDigitResources);
            View[] digitViews = mGlassClockManager.getDigitViews();
            for (View iv : digitViews) {
                iv.setId(View.generateViewId());
                iv.setLayoutParams(new ConstraintLayout.LayoutParams(dpToPx(DIGIT_WIDTH_DP), dpToPx(DIGIT_HEIGHT_DP)));
                iv.setAlpha(0.99f);
                mContainer.addView(iv);
            }
            mColon = createImageView(dpToPx(COLON_WIDTH_DP), dpToPx(COLON_HEIGHT_DP));
            mContainer.addView(mColon);
        } else {
            mHour1 = createImageView(dpToPx(DIGIT_WIDTH_DP), dpToPx(DIGIT_HEIGHT_DP));
            mHour2 = createImageView(dpToPx(DIGIT_WIDTH_DP), dpToPx(DIGIT_HEIGHT_DP));
            mColon = createImageView(dpToPx(COLON_WIDTH_DP), dpToPx(COLON_HEIGHT_DP));
            mMinute1 = createImageView(dpToPx(DIGIT_WIDTH_DP), dpToPx(DIGIT_HEIGHT_DP));
            mMinute2 = createImageView(dpToPx(DIGIT_WIDTH_DP), dpToPx(DIGIT_HEIGHT_DP));

            mContainer.addView(mHour1);
            mContainer.addView(mHour2);
            mContainer.addView(mColon);
            mContainer.addView(mMinute1);
            mContainer.addView(mMinute2);

            ImageView[] digitViews = {mHour1, mHour2, mMinute1, mMinute2};
            mDigitalClockDisplayManager = new DigitalClockDisplayManager(digitViews, mDigitResources);
        }
    }

    private ImageView createImageView(int widthPx, int heightPx) {
        ImageView iv = new ImageView(mContext);
        iv.setId(View.generateViewId());
        iv.setLayoutParams(new ConstraintLayout.LayoutParams(widthPx, heightPx));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    private void setupLayout() {
        LockscreenLayoutManager layoutManager = new LockscreenLayoutManager(mContainer);
        ConstraintSet cs = layoutManager.getConstraintSet();

        int h1, h2, colon, m1, m2;
        if (mUseBlurEffect) {
            View[] digitViews = mGlassClockManager.getDigitViews();
            h1 = digitViews[0].getId();
            h2 = digitViews[1].getId();
            colon = mColon.getId();
            m1 = digitViews[2].getId();
            m2 = digitViews[3].getId();
        } else {
            h1 = mHour1.getId();
            h2 = mHour2.getId();
            colon = mColon.getId();
            m1 = mMinute1.getId();
            m2 = mMinute2.getId();
        }

        int[] timeViewIds = {h1, h2, colon, m1, m2};
        cs.createHorizontalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
            timeViewIds, null, ConstraintSet.CHAIN_PACKED
        );

        int tightSpacing = dpToPx(2);
        int normalSpacing = dpToPx(6);

        cs.setMargin(h1, ConstraintSet.RIGHT, tightSpacing);
        cs.setMargin(h2, ConstraintSet.LEFT, tightSpacing);

        cs.setMargin(h2, ConstraintSet.RIGHT, normalSpacing);
        cs.setMargin(colon, ConstraintSet.LEFT, normalSpacing);

        cs.setMargin(colon, ConstraintSet.RIGHT, normalSpacing);
        cs.setMargin(m1, ConstraintSet.LEFT, normalSpacing);

        cs.setMargin(m1, ConstraintSet.RIGHT, tightSpacing);
        cs.setMargin(m2, ConstraintSet.LEFT, tightSpacing);

        cs.connect(h1, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(100));
        for (int id : new int[]{h2, colon, m1, m2}) {
            cs.connect(id, ConstraintSet.TOP, h1, ConstraintSet.TOP);
            cs.connect(id, ConstraintSet.BOTTOM, h1, ConstraintSet.BOTTOM);
        }

        layoutManager.applyLayoutChanges();
    }

    @Override
    public void onTimeTick() {
        mColon.setImageResource(R.drawable.normaltime_colon);

        String timeString = LockscreenClockUtils.getCurrentTimeString("HHmm");
        if (mUseBlurEffect) {
            mGlassClockManager.updateTime(timeString);
        } else {
            mDigitalClockDisplayManager.updateTimeDisplay(timeString);
        }
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {}

    @Override
    public void applyStyles() {
        if (!mUseBlurEffect) {
            int hourColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getHourColor());
            int minuteColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getMinuteColor());

            mHour1.setColorFilter(hourColor);
            mHour2.setColorFilter(hourColor);
            mColon.setColorFilter(hourColor);
            mMinute1.setColorFilter(minuteColor);
            mMinute2.setColorFilter(minuteColor);
        }
    }

    @Override
    protected void cleanup() {
        if (mGlassClockManager != null) {
            mGlassClockManager.cleanup();
        }
        mContext = null;
        mContainer = null;
        mDigitalClockDisplayManager = null;
    }

    private int dpToPx(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density);
    }
}
