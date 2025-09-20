package org.avium.systemui.lockscreen.type.guoguoclock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.android.systemui.res.R;

import org.avium.systemui.lockscreen.util.BaseLockscreenController;
import org.avium.systemui.lockscreen.util.DigitalClockDisplayManager;
import org.avium.systemui.lockscreen.util.GlassClockManager;
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;
import org.avium.systemui.lockscreen.util.LockscreenLayoutManager;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;

import java.util.Locale;

public class GuoguoClockController4 extends BaseLockscreenController {

    private static final float SCALE_FACTOR = 0.5f;

    private ImageView mHour1, mHour2, mMinute1, mMinute2;
    private ImageView mDotView;
    private ImageView[] mDigitViews;
    private DigitalClockDisplayManager mDigitalClockDisplayManager;

    private boolean mUseBlurEffect;
    private GlassClockManager mGlassClockManager;

    private LockscreenLayoutManager mLayoutManager;

    private final int[] mDigitResources = new int[]{
            R.drawable.avium_guo_type4_0, R.drawable.avium_guo_type4_1, R.drawable.avium_guo_type4_2,
            R.drawable.avium_guo_type4_3, R.drawable.avium_guo_type4_4, R.drawable.avium_guo_type4_5,
            R.drawable.avium_guo_type4_6, R.drawable.avium_guo_type4_7, R.drawable.avium_guo_type4_8,
            R.drawable.avium_guo_type4_9
    };
    private final int mDotResource = R.drawable.avium_guo_type4_dot;

    @Override
    public View getView(Context context) {
        mContext = context;
        mUseBlurEffect =  "blur".equalsIgnoreCase(CustomLockscreenSettings.getClockColor().trim());
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

        Drawable sampleDigitDrawable = mContext.getResources().getDrawable(mDigitResources[0], mContext.getTheme());
        int scaledDigitWidth = (int) (sampleDigitDrawable.getIntrinsicWidth() * SCALE_FACTOR);
        int scaledDigitHeight = (int) (sampleDigitDrawable.getIntrinsicHeight() * SCALE_FACTOR);

        Drawable dotDrawable = mContext.getResources().getDrawable(mDotResource, mContext.getTheme());
        int scaledDotWidth = (int) (dotDrawable.getIntrinsicWidth() * SCALE_FACTOR);
        int scaledDotHeight = (int) (dotDrawable.getIntrinsicHeight() * SCALE_FACTOR);

        mDotView = createImageView();
        mDotView.setLayoutParams(new ConstraintLayout.LayoutParams(scaledDotWidth, scaledDotHeight));
        mDotView.setImageResource(mDotResource);
        mContainer.addView(mDotView);

        if (mUseBlurEffect) {
            mGlassClockManager = new GlassClockManager(mContext, 4, mDigitResources);
            View[] digitViews = mGlassClockManager.getDigitViews();
            for (View iv : digitViews) {
                iv.setId(View.generateViewId());
                iv.setLayoutParams(new ConstraintLayout.LayoutParams(scaledDigitWidth, scaledDigitHeight));
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
                iv.setLayoutParams(new ConstraintLayout.LayoutParams(scaledDigitWidth, scaledDigitHeight));
                mContainer.addView(iv);
            }
            mDigitalClockDisplayManager = new DigitalClockDisplayManager(mDigitViews, mDigitResources);
        }
    }

    private ImageView createImageView() {
        ImageView iv = new ImageView(mContext);
        iv.setId(View.generateViewId());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    private void setupLayout() {
        ConstraintSet cs = mLayoutManager.getConstraintSet();
        View[] digitViews;
        if (mUseBlurEffect) {
            digitViews = mGlassClockManager.getDigitViews();
        } else {
            digitViews = mDigitViews;
        }

        int[] clockChainIds = {
                digitViews[0].getId(), digitViews[1].getId(),
                mDotView.getId(),
                digitViews[2].getId(), digitViews[3].getId()
        };

        cs.createHorizontalChain(
                ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
                ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
                clockChainIds, null, ConstraintSet.CHAIN_PACKED
        );

        for (int id : clockChainIds) {
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
    public void onNotificationStateChanged(boolean hasNotifications) {
    }

    @Override
    public void applyStyles() {
        if (!mUseBlurEffect) {
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
    }

    private int dpToPx(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density);
    }
}
