package org.avium.systemui.lockscreen.type.smallcuteclock;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
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

public class SmallCuteClockController extends BaseLockscreenController {

    private static final int DIGIT_WIDTH_DP = 95;
    private static final int DIGIT_HEIGHT_DP = 135;

    private ImageView mHour1, mHour2, mMinute1, mMinute2;
    private ImageView[] mDigitViews;
    private DigitalClockDisplayManager mDigitalClockDisplayManager;
    private LockscreenLayoutManager mLayoutManager;

    private boolean mUseBlurEffect;
    private GlassClockManager mGlassClockManager;

    private final int[] mDigitResources = new int[]{
        R.drawable.clock_bigboom_00, R.drawable.clock_bigboom_01, R.drawable.clock_bigboom_02,
        R.drawable.clock_bigboom_03, R.drawable.clock_bigboom_04, R.drawable.clock_bigboom_05,
        R.drawable.clock_bigboom_06, R.drawable.clock_bigboom_07, R.drawable.clock_bigboom_08,
        R.drawable.clock_bigboom_09
    };

    @Override
    public View getView(Context context) {
        mContext = context;
        mUseBlurEffect = "blur".equalsIgnoreCase(CustomLockscreenSettings.getClockColor().trim());

        createViews();
        mLayoutManager = new LockscreenLayoutManager(mContainer);
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
        } else {
            mHour1 = createImageView();
            mHour2 = createImageView();
            mMinute1 = createImageView();
            mMinute2 = createImageView();

            mDigitViews = new ImageView[]{mHour1, mHour2, mMinute1, mMinute2};

            for (ImageView iv : mDigitViews) {
                mContainer.addView(iv);
            }
            mDigitalClockDisplayManager = new DigitalClockDisplayManager(mDigitViews, mDigitResources);
        }
    }

    private ImageView createImageView() {
        ImageView iv = new ImageView(mContext);
        iv.setId(View.generateViewId());
        iv.setLayoutParams(new ConstraintLayout.LayoutParams(dpToPx(DIGIT_WIDTH_DP), dpToPx(DIGIT_HEIGHT_DP)));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    private void setupLayout() {
        ConstraintSet cs = mLayoutManager.getConstraintSet();

        int[] clockViewIds;
        if (mUseBlurEffect) {
            View[] digitViews = mGlassClockManager.getDigitViews();
            clockViewIds = new int[]{digitViews[0].getId(), digitViews[1].getId(), digitViews[2].getId(), digitViews[3].getId()};
        } else {
            clockViewIds = new int[]{mHour1.getId(), mHour2.getId(), mMinute1.getId(), mMinute2.getId()};
        }

        cs.createHorizontalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
            clockViewIds, null, ConstraintSet.CHAIN_PACKED
        );

        for (int id : clockViewIds) {
            cs.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(100));
        }

        mLayoutManager.applyLayoutChanges();
    }

    @Override
    public void onTimeTick() {
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

            ImageView[] hourViews = {mHour1, mHour2};
            ImageView[] minuteViews = {mMinute1, mMinute2};
            mDigitalClockDisplayManager.applyColorAndEffects(hourViews, minuteViews);
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
        mLayoutManager = null;
    }

    private int dpToPx(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density);
    }
}
