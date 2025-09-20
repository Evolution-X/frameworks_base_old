package org.avium.systemui.lockscreen.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.avium.systemui.lockscreen.ICustomLockScreenClock;

public abstract class BaseLockscreenController implements ICustomLockScreenClock {

    protected Context mContext;
    protected ConstraintLayout mContainer;
    protected TextView mDateView;
    private boolean mIsReceiverRegistered = false;

    private final BroadcastReceiver mTimeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onTimeTick();
        }
    };

    protected void initializeCommonViews() {
        registerTimeReceiver();
        onTimeTick();
        applyStyles();
        if (mContainer != null) {
            mContainer.setLayoutParams(new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            ));
        }
    }

    protected void updateDateDisplay() {
        if (mDateView != null) {
            mDateView.setText(LockscreenClockUtils.getCurrentDateString());
        }
    }

    @Override
    public void onDestroy() {
        unregisterTimeReceiver();
        cleanup();
    }

    protected abstract void cleanup();

    private void registerTimeReceiver() {
        if (mContext == null || mIsReceiverRegistered) {
            return;
        }
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        mContext.registerReceiver(mTimeReceiver, filter);
        mIsReceiverRegistered = true;
    }

    private void unregisterTimeReceiver() {
        if (mContext != null && mIsReceiverRegistered) {
            mContext.unregisterReceiver(mTimeReceiver);
            mIsReceiverRegistered = false;
        }
    }
}