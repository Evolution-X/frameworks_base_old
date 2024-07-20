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

import android.content.Intent;
import android.provider.Settings;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.PorterDuff;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionLegacyHelper;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.hardware.camera2.CameraManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.graphics.ColorUtils;

import com.android.settingslib.Utils;

import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.animation.view.LaunchableImageView;
import com.android.systemui.lockscreen.ActivityLauncherUtils;
import com.android.systemui.media.dialog.MediaOutputDialog;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.qs.VerticalSlider;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.NotificationMediaManager;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import android.os.SystemClock;
import android.view.KeyEvent;

public class QsControlsView extends FrameLayout {

    private final static String PERSONALIZATIONS_ACTIVITY = "com.android.settings.Settings$PersonalizationsActivity";

    private List<View> mControlTiles = new ArrayList<>();
    private List<View> mMediaPlayerViews = new ArrayList<>();
    private List<View> mWidgetViews = new ArrayList<>();
    private List<Runnable> metadataCheckRunnables = new ArrayList<>();

    private View mSettingsButton, mVoiceAssist, mRunningServiceButton, mInterfaceButton, mMediaCard, mAccessBg, mWidgetsBg;
    private View mClockTimer, mCalculator, mCamera, mPagerLayout, mMediaLayout, mAccessLayout, mWidgetsLayout;
    private ImageView mTorch;
    
    private QsControlsPageIndicator mAccessPageIndicator, mMediaPageIndicator, mWidgetsPageIndicator;
    private VerticalSlider mBrightnessSlider, mVolumeSlider;

    private final ActivityStarter mActivityStarter;
    private final FalsingManager mFalsingManager;
    private final FlashlightController mFlashlightController;
    private final NotificationMediaManager mNotifManager;
    private final ActivityLauncherUtils mActivityLauncherUtils;

    private ViewPager mViewPager;
    private PagerAdapter pagerAdapter;

    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashOn = false;

    private int mAccentColor, mBgColor, mTintColor, mContainerColor;
    
    private Context mContext;

    private TextView mMediaTitle, mMediaArtist;
    private ImageView mMediaPrevBtn, mMediaPlayBtn, mMediaNextBtn, mMediaAlbumArtBg, mPlayerIcon;
    
    private MediaController mController;
    private MediaMetadata mMediaMetadata;
    private boolean mInflated = false;
    private Bitmap mAlbumArt = null;
    
    private boolean isClearingMetadata = false;
    
    private Handler mHandler;
    private Runnable mMediaUpdater;

    public QsControlsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mPagerLayout = LayoutInflater.from(mContext).inflate(R.layout.qs_controls_tile_pager, null);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (Exception e) {}
        mActivityStarter = Dependency.get(ActivityStarter.class);
        mFalsingManager = Dependency.get(FalsingManager.class);
        mNotifManager = Dependency.get(NotificationMediaManager.class);
        mFlashlightController = Dependency.get(FlashlightController.class);
        mActivityLauncherUtils = new ActivityLauncherUtils(context);
    }

    private final MediaController.Callback mMediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackState state) {
            updateMediaController();
        }
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            mMediaMetadata = metadata;
            updateMediaController();
        }
    };
    
    private final FlashlightController.FlashlightListener mFlashlightCallback =
            new FlashlightController.FlashlightListener() {

        @Override
        public void onFlashlightChanged(boolean enabled) {
            isFlashOn = enabled;
            updateTiles();
        }

        @Override
        public void onFlashlightError() {
        }

        @Override
        public void onFlashlightAvailabilityChanged(boolean available) {
            isFlashOn = mFlashlightController.isEnabled() && available;
            updateTiles();
        }
    };

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE && isAttachedToWindow()) {
            updateMediaController();
            updateResources();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mInflated = true;
		mViewPager = findViewById(R.id.qs_controls_pager);
        mBrightnessSlider = findViewById(R.id.qs_controls_brightness_slider);
        mVolumeSlider = findViewById(R.id.qs_controls_volume_slider);
        mMediaLayout = mPagerLayout.findViewById(R.id.qs_controls_media);
        mAccessLayout = mPagerLayout.findViewById(R.id.qs_controls_tile_access);
        mWidgetsLayout = mPagerLayout.findViewById(R.id.qs_controls_tile_widgets);
        mVoiceAssist = mAccessLayout.findViewById(R.id.qs_voice_assist);
        mSettingsButton = mAccessLayout.findViewById(R.id.settings_button);
        mRunningServiceButton = mAccessLayout.findViewById(R.id.running_services_button);
        mInterfaceButton = mAccessLayout.findViewById(R.id.interface_button);
        mTorch = mWidgetsLayout.findViewById(R.id.qs_flashlight);
        mClockTimer = mWidgetsLayout.findViewById(R.id.qs_clock_timer);
        mCalculator = mWidgetsLayout.findViewById(R.id.qs_calculator);
        mCamera = mWidgetsLayout.findViewById(R.id.qs_camera);
        mMediaAlbumArtBg = mMediaLayout.findViewById(R.id.media_art_bg);
        mMediaAlbumArtBg.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mMediaTitle = mMediaLayout.findViewById(R.id.media_title);
        mMediaArtist = mMediaLayout.findViewById(R.id.artist_name);
        mMediaPrevBtn = mMediaLayout.findViewById(R.id.previous_button);
        mMediaPlayBtn = mMediaLayout.findViewById(R.id.play_button);
        mMediaNextBtn = mMediaLayout.findViewById(R.id.next_button);
        mPlayerIcon = mMediaLayout.findViewById(R.id.player_icon);
        mMediaCard = mMediaLayout.findViewById(R.id.media_cardview);
        mAccessBg = mAccessLayout.findViewById(R.id.qs_controls_access_layout);
        mWidgetsBg = mWidgetsLayout.findViewById(R.id.qs_controls_widgets_layout);
        mAccessPageIndicator = mAccessLayout.findViewById(R.id.access_page_indicator);
        mMediaPageIndicator = mMediaLayout.findViewById(R.id.media_page_indicator);
        mWidgetsPageIndicator = mWidgetsLayout.findViewById(R.id.widgets_page_indicator);
        collectViews(mControlTiles, mVoiceAssist, mSettingsButton, mRunningServiceButton, 
            mInterfaceButton, (View) mTorch, mClockTimer, mCalculator, mCamera);
        collectViews(mMediaPlayerViews, mMediaPrevBtn, mMediaPlayBtn, mMediaNextBtn, 
                mMediaAlbumArtBg, mPlayerIcon, mMediaTitle, mMediaArtist);
        collectViews(mWidgetViews, mMediaLayout, mAccessLayout, mWidgetsLayout);
        setupViewPager();
	mHandler = new Handler();
        mMediaUpdater = new Runnable() {
            @Override
            public void run() {
                updateMediaController();
                mHandler.postDelayed(this, 1000);
            }
        };
        updateMediaController();
	}

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mInflated) {
            return;
        }
        setClickListeners();
        updateResources();
        mFlashlightController.addCallback(mFlashlightCallback);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFlashlightController.removeCallback(mFlashlightCallback);
    }

    private void launchActivitySafely(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            // Handle exception when user is locked or not running
            if (e.getMessage() != null && 
                (e.getMessage().contains("locked") || 
                 e.getMessage().contains("not running"))) {
                // Log the error but don't crash
                android.util.Log.e("QsControlsView", 
                    "Failed to launch activity: User locked or not running", e);
            } else {
                // For other exceptions, rethrow
                throw new RuntimeException(e);
            }
        }
    }

    private void setClickListeners() {
        mTorch.setOnClickListener(view -> toggleFlashlight());
        mClockTimer.setOnClickListener(view -> launchActivitySafely(() -> 
            mActivityLauncherUtils.launchTimer()));
        mCalculator.setOnClickListener(view -> launchActivitySafely(() -> 
            mActivityLauncherUtils.launchCalculator()));
        mVoiceAssist.setOnClickListener(view -> launchActivitySafely(() -> 
            mActivityLauncherUtils.launchVoiceAssistant()));
        mCamera.setOnClickListener(view -> launchActivitySafely(() -> 
            mActivityLauncherUtils.launchCamera()));
        mSettingsButton.setOnClickListener(mSettingsOnClickListener);
        mRunningServiceButton.setOnClickListener(mSettingsOnClickListener);
        mInterfaceButton.setOnClickListener(mSettingsOnClickListener);
        mMediaPlayBtn.setOnClickListener(view -> performMediaAction(MediaAction.TOGGLE_PLAYBACK));
        mMediaPrevBtn.setOnClickListener(view -> performMediaAction(MediaAction.PLAY_PREVIOUS));
        mMediaNextBtn.setOnClickListener(view -> performMediaAction(MediaAction.PLAY_NEXT));
        mMediaAlbumArtBg.setOnClickListener(view -> launchActivitySafely(() -> 
            mActivityLauncherUtils.launchMediaPlayerApp()));
        ((LaunchableImageView) mMediaAlbumArtBg).setOnLongClickListener(view -> {
            showMediaOutputDialog();
            return true;
        });
    }

    private void clearMediaMetadata() {
        if (isClearingMetadata) return;
        isClearingMetadata = true;
        mMediaMetadata = null;
        mAlbumArt = null; 
        isClearingMetadata = false;
        if (mMediaPlayBtn != null) {
            mMediaPlayBtn.setImageResource(R.drawable.ic_media_play);
        }
    }
    
    private void updateMediaController() {
        MediaController localController = getActiveLocalMediaController();
        if (localController != null && !mNotifManager.sameSessions(mController, localController)) {
            if (mController != null) {
                mController.unregisterCallback(mMediaCallback);
                mController = null;
            }
            mController = localController;
            mController.registerCallback(mMediaCallback);
        }
        mMediaMetadata = isMediaControllerAvailable() ? mController.getMetadata() : null;
        updateMediaPlaybackState();
    }

    private MediaController getActiveLocalMediaController() {
        MediaSessionManager mediaSessionManager =
                mContext.getSystemService(MediaSessionManager.class);
        MediaController localController = null;
        final List<String> remoteMediaSessionLists = new ArrayList<>();
        for (MediaController controller : mediaSessionManager.getActiveSessions(null)) {
            final MediaController.PlaybackInfo pi = controller.getPlaybackInfo();
            if (pi == null) {
                continue;
            }
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState == null) {
                continue;
            }
            if (playbackState.getState() != PlaybackState.STATE_PLAYING) {
                continue;
            }
            if (pi.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
                if (localController != null
                        && TextUtils.equals(
                                localController.getPackageName(), controller.getPackageName())) {
                    localController = null;
                }
                if (!remoteMediaSessionLists.contains(controller.getPackageName())) {
                    remoteMediaSessionLists.add(controller.getPackageName());
                }
                continue;
            }
            if (pi.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL) {
                if (localController == null
                        && !remoteMediaSessionLists.contains(controller.getPackageName())) {
                    localController = controller;
                }
            }
        }
        return localController;
    }

    private boolean isMediaControllerAvailable() {
        final MediaController mediaController = getActiveLocalMediaController();
        return mediaController != null && !TextUtils.isEmpty(mediaController.getPackageName());
    }

    private void updateMediaPlaybackState() {
        updateMediaMetadata();
        postDelayed(() -> {
            updateMediaMetadata();
        }, 250);
    }

    private void updateMediaMetadata() {
        Bitmap albumArt = mMediaMetadata == null ? null : mMediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (albumArt != null) {
            new ProcessArtworkTask().execute(albumArt);
        } else {
            mMediaAlbumArtBg.setImageBitmap(null);
        }
        updateMediaViews();
    }
    
    private boolean isMediaPlaying() {
        return isMediaControllerAvailable() 
            && PlaybackState.STATE_PLAYING == mNotifManager.getMediaControllerPlaybackState(mController);
    }

    private void updateMediaViews() {
        if (!isMediaPlaying()) {
            clearMediaMetadata();
        }
        if (mMediaPlayBtn != null) {
            mMediaPlayBtn.setImageResource(isMediaPlaying() ? R.drawable.ic_media_pause : R.drawable.ic_media_play);
        }
        CharSequence title = mMediaMetadata == null ? null : mMediaMetadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence artist = mMediaMetadata == null ? null : mMediaMetadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
        mMediaTitle.setText(title != null ? title : mContext.getString(R.string.no_media_playing));
        mMediaArtist.setText(artist != null ? artist : "");
        mPlayerIcon.setImageIcon(mNotifManager == null ? null : mNotifManager.getMediaIcon());
        final int mediaItemColor = getMediaItemColor();
        for (View view : mMediaPlayerViews) {
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(mediaItemColor);
            } else if (view instanceof ImageView) {
                ((ImageView) view).setImageTintList(ColorStateList.valueOf(mediaItemColor));
            }
        }
    }

    private class ProcessArtworkTask extends AsyncTask<Bitmap, Void, Bitmap> {
        protected Bitmap doInBackground(Bitmap... bitmaps) {
            Bitmap bitmap = bitmaps[0];
            if (bitmap == null) {
                return null;
            }
            int width = mMediaAlbumArtBg.getWidth();
            int height = mMediaAlbumArtBg.getHeight();
            return getScaledRoundedBitmap(bitmap, width, height);
        }
        protected void onPostExecute(Bitmap result) {
            if (result == null) return;
            if (mAlbumArt == null || mAlbumArt != result) {
                mAlbumArt = result;
                final int mediaFadeLevel = mContext.getResources().getInteger(R.integer.media_player_fade);
                final int fadeFilter = ColorUtils.blendARGB(Color.TRANSPARENT, mNotifManager == null ? Color.BLACK : mNotifManager.getMediaBgColor(), mediaFadeLevel / 100f);
                mMediaAlbumArtBg.setColorFilter(fadeFilter, PorterDuff.Mode.SRC_ATOP);
                mMediaAlbumArtBg.setImageBitmap(mAlbumArt);
            }
        }
    }

    private Bitmap getScaledRoundedBitmap(Bitmap bitmap, int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        float radius = mContext.getResources().getDimensionPixelSize(R.dimen.qs_controls_slider_corner_radius);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        if (scaledBitmap == null) {
            return null;
        }
        Bitmap output = Bitmap.createBitmap(scaledBitmap.getWidth(), scaledBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        RectF rect = new RectF(0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());
        canvas.drawRoundRect(rect, radius, radius, paint);
        return output;
    }

    private void setupViewPager() {
        if (mViewPager == null || mWidgetViews == null) return;

        pagerAdapter = new PagerAdapter() {
            @Override
            public int getCount() {
                return mWidgetViews.size();
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                View view = mWidgetViews.get(position);

                // Ensure the view is removed from its previous parent before adding it
                if (view.getParent() != null) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }

                container.addView(view);
                return view;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                container.removeView((View) object);
            }
        };

        mViewPager.setAdapter(pagerAdapter);
        mAccessPageIndicator.setupWithViewPager(mViewPager);
        mMediaPageIndicator.setupWithViewPager(mViewPager);
        mWidgetsPageIndicator.setupWithViewPager(mViewPager);

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                mMediaLayout.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                mAccessLayout.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                mWidgetsLayout.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });
    }

    public void updateColors() {
        mAccentColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_active_color_dark : R.color.lockscreen_widget_active_color_light);
        mBgColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_bg_color_dark : R.color.qs_controls_bg_color_light);
        mTintColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_bg_color_light : R.color.qs_controls_bg_color_dark);
        mContainerColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_container_bg_color_dark : R.color.qs_controls_container_bg_color_light);
        updateTiles();
        if (mAccessBg != null && mMediaCard != null && mWidgetsBg != null) {
            mMediaCard.getBackground().setTint(mContainerColor);
            mAccessBg.setBackgroundTintList(ColorStateList.valueOf(mContainerColor));
            mWidgetsBg.setBackgroundTintList(ColorStateList.valueOf(mContainerColor));
        }
        if (mAccessPageIndicator != null && mMediaPageIndicator != null && mWidgetsPageIndicator != null) {
            mAccessPageIndicator.updateColors(isNightMode());
            mMediaPageIndicator.updateColors(isNightMode());
            mWidgetsPageIndicator.updateColors(isNightMode());
        }
        updateMediaPlaybackState();
    }
    
    private boolean isNightMode() {
        return (mContext.getResources().getConfiguration().uiMode 
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
    
    private void updateTiles() {
        for (View view : mControlTiles) {
            if (view instanceof ImageView) {
                ImageView tile = (ImageView) view;
                int backgroundResource;
                int imageTintColor;
                int backgroundTintColor;
                if (tile == mTorch) {
                    backgroundResource = isFlashOn ? R.drawable.qs_controls_tile_background_active : R.drawable.qs_controls_tile_background;
                    imageTintColor = isFlashOn ? mBgColor : mTintColor;
                    backgroundTintColor = isFlashOn ? mAccentColor : mBgColor;
                } else if (tile == mInterfaceButton || tile == mCamera) {
                    backgroundResource = R.drawable.qs_controls_tile_background_active;
                    imageTintColor = mBgColor;
                    backgroundTintColor = mAccentColor;
                } else {
                    backgroundResource = R.drawable.qs_controls_tile_background;
                    imageTintColor = mTintColor;
                    backgroundTintColor = mBgColor;
                }
                tile.setBackgroundResource(backgroundResource);
                tile.setImageTintList(ColorStateList.valueOf(imageTintColor));
                tile.setBackgroundTintList(ColorStateList.valueOf(backgroundTintColor));
            }
        }
    }
    
    private int getMediaItemColor() {
        return isMediaPlaying() ? Color.WHITE : mTintColor;
    }

    private final View.OnClickListener mSettingsOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mFalsingManager != null && mFalsingManager.isFalseTap(FalsingManager.LOW_PENALTY)) {
                return;
            }
            if (v == mSettingsButton) {
                launchActivitySafely(() -> mActivityLauncherUtils.startSettingsActivity());
            } else if (v == mRunningServiceButton) {
                launchActivitySafely(() -> mActivityLauncherUtils.launchSettingsComponent(
                    "com.android.settings.Settings$DevRunningServicesActivity"));
            } else if (v == mInterfaceButton) {
                launchActivitySafely(() -> mActivityLauncherUtils.launchSettingsComponent(
                    PERSONALIZATIONS_ACTIVITY));
            }
        }
    };

    public void updateResources() {
        if (mBrightnessSlider != null && mVolumeSlider != null) {
            mBrightnessSlider.updateSliderPaint();
            mVolumeSlider.updateSliderPaint();
        }
        updateColors();
    }

    private void collectViews(List<View> viewList, View... views) {
        for (View view : views) {
            if (!viewList.contains(view)) {
                viewList.add(view);
            }
        }
    }

    private void toggleFlashlight() {
        if (mActivityStarter == null) return;
        try {
            cameraManager.setTorchMode(cameraId, !isFlashOn);
            isFlashOn = !isFlashOn;
            int tintColor = isFlashOn ? mBgColor : mTintColor;
            int bgColor = isFlashOn ? mAccentColor : mBgColor;
            mTorch.setBackgroundResource(isFlashOn ?  R.drawable.qs_controls_tile_background_active :  R.drawable.qs_controls_tile_background);
            mTorch.setImageTintList(ColorStateList.valueOf(tintColor));
            mTorch.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        } catch (Exception e) {}
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    private void performMediaAction(MediaAction action) {
        updateMediaController();
        switch (action) {
            case TOGGLE_PLAYBACK:
                toggleMediaPlaybackState();
                break;
            case PLAY_PREVIOUS:
                dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
            case PLAY_NEXT:
                dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
        }
        updateMediaPlaybackState();
    }
    
    private void toggleMediaPlaybackState() {
        if (isMediaPlaying()) {
            mHandler.removeCallbacks(mMediaUpdater);
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PAUSE);
            updateMediaController();
            if (mMediaPlayBtn != null) {
                mMediaPlayBtn.setImageResource(R.drawable.ic_media_play);
            }
        } else {
            mMediaUpdater.run();
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PLAY);
            if (mMediaPlayBtn != null) {
                mMediaPlayBtn.setImageResource(R.drawable.ic_media_pause);
            }
        }
    }
    
    private void dispatchMediaKeyWithWakeLockToMediaSession(final int keycode) {
        final MediaSessionLegacyHelper helper = MediaSessionLegacyHelper.getHelper(mContext);
        if (helper == null) {
            return;
        }
        KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keycode, 0);
        helper.sendMediaButtonEvent(event, true);
        event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
        helper.sendMediaButtonEvent(event, true);
    }

    private void showMediaOutputDialog() {
        try {
            String packageName = mActivityLauncherUtils.getActiveMediaPackage();
            if (!packageName.isEmpty()) {
                Intent intent = new Intent();
                intent.setAction("android.settings.panel.action.MEDIA_OUTPUT");
                intent.putExtra("android.provider.extra.PACKAGE_NAME", packageName);
                mActivityStarter.startActivity(intent, true);
            }
        } catch (Exception e) {
            // Handle exception when user is locked or not running
            if (e.getMessage() != null && 
                (e.getMessage().contains("locked") || 
                 e.getMessage().contains("not running"))) {
                android.util.Log.e("QsControlsView", 
                    "Failed to show media output dialog: User locked or not running", e);
            } else {
                throw new RuntimeException(e);
            }
        }
    }
    
    private enum MediaAction {
        TOGGLE_PLAYBACK,
        PLAY_PREVIOUS,
        PLAY_NEXT
    }
}
