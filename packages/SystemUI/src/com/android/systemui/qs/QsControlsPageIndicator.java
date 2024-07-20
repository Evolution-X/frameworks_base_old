/*
 * Copyright (C) 2024 the risingOS Android Project
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
package com.android.systemui.qs;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import com.android.systemui.R;

public class QsControlsPageIndicator extends LinearLayout {
    private Context mContext;
    private int mDrawableSelected;
    private int mDrawableUnselected;
    private int mTotalPages;
    private int mSelectedPageIndex = 0;
    private ViewPager mViewPager;

    private int mAccentColor, mBgColor;

    public QsControlsPageIndicator(Context context) {
        super(context);
        init(context);
    }

    public QsControlsPageIndicator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public QsControlsPageIndicator(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
    }

    public void setupWithViewPager(ViewPager viewPager) {
        mViewPager = viewPager;
        mTotalPages = viewPager.getAdapter() != null ? viewPager.getAdapter().getCount() : 0;
        if (mTotalPages > 0) {
            createIndicators();
            selectIndicator(0);
            viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    selectIndicator(position);
                }
            });
        }
    }

    public void updateColors(boolean isNightMode) {
        mAccentColor = mContext.getColor(isNightMode ? R.color.qs_controls_active_color_dark : R.color.lockscreen_widget_active_color_light);
        mBgColor = mContext.getColor(isNightMode ? R.color.qs_controls_bg_color_dark : R.color.qs_controls_bg_color_light);
        setupWithViewPager(mViewPager);
    }

    private void createIndicators() {
        removeAllViews();
        int indicatorSize = getResources().getDimensionPixelSize(R.dimen.qs_controls_page_indicator_size);
        int indicatorMargin = getResources().getDimensionPixelSize(R.dimen.qs_controls_page_indicator_margin);

        for (int i = 0; i < mTotalPages; i++) {
            ImageView indicator = new ImageView(mContext);
            LayoutParams params = new LayoutParams(indicatorSize, indicatorSize);
            params.setMargins(indicatorMargin, 0, indicatorMargin, 0);
            indicator.setLayoutParams(params);
            if (i == mSelectedPageIndex) {
                indicator.setImageResource(R.drawable.viewpager_dot_selected);
                indicator.setImageTintList(ColorStateList.valueOf(mAccentColor));
            } else {
                indicator.setImageResource(R.drawable.viewpager_dot_unselected);
                indicator.setImageTintList(ColorStateList.valueOf(mBgColor));
            }
            addView(indicator);
        }
    }

    private void selectIndicator(int position) {
        if (position >= 0 && position < mTotalPages) {
            for (int i = 0; i < getChildCount(); i++) {
                ImageView indicator = (ImageView) getChildAt(i);
                if (i == position) {
                    indicator.setImageResource(R.drawable.viewpager_dot_selected);
                    indicator.setImageTintList(ColorStateList.valueOf(mAccentColor));
                } else {
                    indicator.setImageResource(R.drawable.viewpager_dot_unselected);
                    indicator.setImageTintList(ColorStateList.valueOf(mBgColor));
                }
            }
            mSelectedPageIndex = position;
        }
    }
}
