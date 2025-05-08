/*
 * Copyright (C) 2024 the risingOS Android Project
 * Copyright (C) 2025 the RisingOS Revived Android Project
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
import android.bluetooth.BluetoothAdapter;
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
import com.android.systemui.animation.Expandable;
import com.android.systemui.animation.view.LaunchableImageView;
import com.android.systemui.animation.view.LaunchableLinearLayout;
import com.android.systemui.lockscreen.ActivityLauncherUtils;
import com.android.systemui.media.dialog.MediaOutputDialog;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.qs.VerticalSlider;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.connectivity.AccessPointController;
import com.android.systemui.statusbar.connectivity.IconState;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.SignalCallback;
import com.android.systemui.statusbar.connectivity.MobileDataIndicators;
import com.android.systemui.statusbar.connectivity.WifiIndicators;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BluetoothController.Callback;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.bluetooth.qsdialog.BluetoothTileDialogViewModel;
import com.android.systemui.qs.tiles.dialog.InternetDialogManager;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import android.os.SystemClock;
import android.view.KeyEvent;
import android.text.TextUtils;
import com.android.internal.util.android.VibrationUtils;

public class QsControlsView extends FrameLayout {

    private final static String PERSONALIZATIONS_ACTIVITY = "com.android.settings.Settings$PersonalizationsActivity";

    private List<View> mControlTiles = new ArrayList<>();
    private List<View> mMediaPlayerViews = new ArrayList<>();
    private List<View> mConnectivityTiles = new ArrayList<>();
    private List<View> mWidgetViews = new ArrayList<>();
    private List<Runnable> metadataCheckRunnables = new ArrayList<>();

    public static final int BT_ACTIVE = R.drawable.qs_bluetooth_icon_on;
    public static final int BT_INACTIVE = R.drawable.qs_bluetooth_icon_off;
    public static final int DATA_ACTIVE = R.drawable.ic_signal_cellular_alt_24;
    public static final int DATA_INACTIVE = R.drawable.ic_mobiledata_off_24;
    public static final int WIFI_ACTIVE = R.drawable.ic_wifi_24;
    public static final int WIFI_INACTIVE = R.drawable.ic_wifi_off_24;

    public static final int BT_LABEL_INACTIVE = R.string.quick_settings_bluetooth_label;
    public static final int DATA_LABEL_INACTIVE = R.string.quick_settings_data_label;
    public static final int INTERNET_LABEL_INACTIVE = R.string.quick_settings_internet_label;
    public static final int WIFI_LABEL_INACTIVE = R.string.quick_settings_wifi_label;

    private View mSettingsButton, mVoiceAssist, mRunningServiceButton, mInterfaceButton, mMediaCard, mAccessBg, mWidgetsBg, mConnectivityBg;
    private View mClockTimer, mCalculator, mCamera, mPagerLayout, mMediaLayout, mAccessLayout, mWidgetsLayout, mConnectivityLayout;
    private LaunchableLinearLayout mInternetButton, mBtButton;
    private ImageView mTorch;
    
    private QsControlsPageIndicator mAccessPageIndicator, mMediaPageIndicator, mWidgetsPageIndicator;
    private VerticalSlider mBrightnessSlider, mVolumeSlider;

    private final ActivityStarter mActivityStarter;
    private final FalsingManager mFalsingManager;
    private final FlashlightController mFlashlightController;
    private final NotificationMediaManager mNotifManager;
    private final AccessPointController mAccessPointController;
    private final NetworkController mNetworkController;
    private final BluetoothController mBluetoothController;
    private final BluetoothTileDialogViewModel mBluetoothTileDialogViewModel;
    private final InternetDialogManager mInternetDialogManager;

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

    protected final CellSignalCallback mCellSignalCallback = new CellSignalCallback();
    protected final WifiSignalCallback mWifiSignalCallback = new WifiSignalCallback();

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
        mAccessPointController = Dependency.get(AccessPointController.class);
        mBluetoothController = Dependency.get(BluetoothController.class);
        mBluetoothTileDialogViewModel = Dependency.get(BluetoothTileDialogViewModel.class);
        mInternetDialogManager = Dependency.get(InternetDialogManager.class);
        mNetworkController = Dependency.get(NetworkController.class);

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
        
        // Initialize all the layout views
        mConnectivityLayout = findViewById(R.id.qs_controls_tile_connectivity);
        mMediaLayout = findViewById(R.id.qs_controls_media);
        mAccessLayout = findViewById(R.id.qs_controls_tile_access);
        mWidgetsLayout = findViewById(R.id.qs_controls_tile_widgets);
        
        // Initialize components in the access layout
        mVoiceAssist = mAccessLayout.findViewById(R.id.qs_voice_assist);
        mSettingsButton = mAccessLayout.findViewById(R.id.settings_button);
        mRunningServiceButton = mAccessLayout.findViewById(R.id.running_services_button);
        mInterfaceButton = mAccessLayout.findViewById(R.id.interface_button);
        
        // Initialize components in the connectivity layout
        mInternetButton = mConnectivityLayout.findViewById(R.id.internet_btn);
        mBtButton = mConnectivityLayout.findViewById(R.id.bt_btn);
        
        // Initialize components in the widgets layout
        mTorch = mWidgetsLayout.findViewById(R.id.qs_flashlight);
        mClockTimer = mWidgetsLayout.findViewById(R.id.qs_clock_timer);
        mCalculator = mWidgetsLayout.findViewById(R.id.qs_calculator);
        mCamera = mWidgetsLayout.findViewById(R.id.qs_camera);
        
        // Initialize media player components
        mMediaAlbumArtBg = mMediaLayout.findViewById(R.id.media_art_bg);
        mMediaAlbumArtBg.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mMediaTitle = mMediaLayout.findViewById(R.id.media_title);
        mMediaArtist = mMediaLayout.findViewById(R.id.artist_name);
        mMediaPrevBtn = mMediaLayout.findViewById(R.id.previous_button);
        mMediaPlayBtn = mMediaLayout.findViewById(R.id.play_button);
        mMediaNextBtn = mMediaLayout.findViewById(R.id.next_button);
        mPlayerIcon = mMediaLayout.findViewById(R.id.player_icon);
        mMediaCard = mMediaLayout.findViewById(R.id.media_cardview);
        
        // Initialize background/container views
        mAccessBg = mAccessLayout.findViewById(R.id.qs_controls_access_layout);
        mConnectivityBg = mConnectivityLayout.findViewById(R.id.qs_controls_connectivity_layout);
        mWidgetsBg = mWidgetsLayout.findViewById(R.id.qs_controls_widgets_layout);
        
        // Initialize page indicators
        mAccessPageIndicator = mAccessLayout.findViewById(R.id.access_page_indicator);
        mMediaPageIndicator = mMediaLayout.findViewById(R.id.media_page_indicator);
        mWidgetsPageIndicator = mWidgetsLayout.findViewById(R.id.widgets_page_indicator);
        
        // Clear the widget views list to avoid duplicates
        mWidgetViews.clear();
        
        // Add the main pages to the widget views list in proper order
        // This order determines the page sequence in the ViewPager
        collectViews(mWidgetViews, mMediaLayout, mConnectivityLayout, mAccessLayout, mWidgetsLayout);
        
        // Collect control tiles
        collectViews(mConnectivityTiles, mInternetButton, mBtButton);
        collectViews(mControlTiles, mVoiceAssist, mSettingsButton, mRunningServiceButton, 
            mInterfaceButton, (View) mTorch, mClockTimer, mCalculator, mCamera);
        collectViews(mMediaPlayerViews, mMediaPrevBtn, mMediaPlayBtn, mMediaNextBtn, 
                mMediaAlbumArtBg, mPlayerIcon, mMediaTitle, mMediaArtist);
        
        // Setup the ViewPager with the collected views
        setupViewPager();
        
        // Initialize handler for media updates
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
        mBluetoothController.addCallback(mBtCallback);
        mNetworkController.addCallback(mWifiSignalCallback);
        mNetworkController.addCallback(mCellSignalCallback);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFlashlightController.removeCallback(mFlashlightCallback);
    }

    private final BluetoothController.Callback mBtCallback = new BluetoothController.Callback() {
        @Override
        public void onBluetoothStateChange(boolean enabled) {
            updateBtState();
        }

        @Override
        public void onBluetoothDevicesChanged() {
            updateBtState();
        }
    };

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
        mBtButton.setOnClickListener(view -> toggleBluetoothState());
        mBtButton.setOnLongClickListener(v -> { showBluetoothDialog(v); return true; });
        mInternetButton.setOnClickListener(view -> showInternetDialog(view));
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
        if (mViewPager == null || mWidgetViews == null || mWidgetViews.isEmpty()) return;

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

                // Make sure the view isn't already attached to another parent
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null && parent != container) {
                    parent.removeView(view);
                }

                // Only add the view if it's not already in the container
                if (view.getParent() == null) {
                    container.addView(view);
                }

                return view;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                // We don't actually want to remove these Views since we're reusing them
                // Just leave them in the view hierarchy but make them GONE
                View view = (View) object;
                if (view.getParent() == container) {
                    view.setVisibility(View.GONE);
                }
            }
        };

        mViewPager.setAdapter(pagerAdapter);

        // Configure all page indicators with the correct count
        int pageCount = mWidgetViews.size();
        if (mAccessPageIndicator != null) {
            mAccessPageIndicator.setupWithViewPager(mViewPager);
            mAccessPageIndicator.setPageCount(pageCount);
        }

        if (mMediaPageIndicator != null) {
            mMediaPageIndicator.setupWithViewPager(mViewPager);
            mMediaPageIndicator.setPageCount(pageCount);
        }

        if (mWidgetsPageIndicator != null) {
            mWidgetsPageIndicator.setupWithViewPager(mViewPager);
            mWidgetsPageIndicator.setPageCount(pageCount);
        }

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // Update page indicators during scrolling
                if (mAccessPageIndicator != null) {
                    mAccessPageIndicator.onPageScrolled(position, positionOffset);
                }
                if (mMediaPageIndicator != null) {
                    mMediaPageIndicator.onPageScrolled(position, positionOffset);
                }
                if (mWidgetsPageIndicator != null) {
                    mWidgetsPageIndicator.onPageScrolled(position, positionOffset);
                }
            }

            @Override
            public void onPageSelected(int position) {
                // Update visibility of each page based on selection
                updatePageVisibility(position);

                // Update page indicators
                if (mAccessPageIndicator != null) {
                    mAccessPageIndicator.setCurrentItem(position);
                }
                if (mMediaPageIndicator != null) {
                    mMediaPageIndicator.setCurrentItem(position);
                }
                if (mWidgetsPageIndicator != null) {
                    mWidgetsPageIndicator.setCurrentItem(position);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });

        // Set initial page and update visibility
        int initialPage = 0;
        mViewPager.setCurrentItem(initialPage);
        updatePageVisibility(initialPage);
    }

    // Helper method to update page visibility based on selected position
    private void updatePageVisibility(int selectedPosition) {
        if (mWidgetViews == null) return;

        // Make all pages visible initially (important for the pager to measure them)
        for (View view : mWidgetViews) {
            view.setVisibility(View.VISIBLE);
        }

        // Debugging log - print the number of widget views
        android.util.Log.d("QsControlsView", "Page count: " + mWidgetViews.size() + ", Selected: " + selectedPosition);

        // Update corresponding layouts based on position
        mMediaLayout.setVisibility(selectedPosition == 0 ? View.VISIBLE : View.GONE);
        mConnectivityLayout.setVisibility(selectedPosition == 1 ? View.VISIBLE : View.GONE);
        mAccessLayout.setVisibility(selectedPosition == 2 ? View.VISIBLE : View.GONE);
        mWidgetsLayout.setVisibility(selectedPosition == 3 ? View.VISIBLE : View.GONE);
    }

    public void updateColors() {
        mAccentColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_active_color_dark : R.color.lockscreen_widget_active_color_light);
        mBgColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_bg_color_dark : R.color.qs_controls_bg_color_light);
        mTintColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_bg_color_light : R.color.qs_controls_bg_color_dark);
        mContainerColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_container_bg_color_dark : R.color.qs_controls_container_bg_color_light);
        updateConnectivityTiles();
	updateTiles();
        if (mAccessBg != null && mMediaCard != null && mWidgetsBg != null && mConnectivityBg != null) {
            mMediaCard.getBackground().setTint(mContainerColor);
            mAccessBg.setBackgroundTintList(ColorStateList.valueOf(mContainerColor));
            mWidgetsBg.setBackgroundTintList(ColorStateList.valueOf(mContainerColor));
        }
        if (mAccessPageIndicator != null && mMediaPageIndicator != null && mWidgetsPageIndicator != null) {
            mAccessPageIndicator.updateColors(isNightMode());
            mMediaPageIndicator.updateColors(isNightMode());
            mWidgetsPageIndicator.updateColors(isNightMode());
        }
        updateInternetButtonState();
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

    /**
     * Collects views into the provided list.
     * If collecting main page views (mMediaLayout, mConnectivityLayout, etc.), 
     * the list is cleared first to prevent duplicates.
     * 
     * @param viewList The list to add views to
     * @param views The views to add to the list
     */
    private void collectViews(List<View> viewList, View... views) {
        // Determine if we're collecting main page views
        boolean isCollectingMainViews = views.length > 0 && 
            (views[0] == mMediaLayout || views[0] == mConnectivityLayout || 
             views[0] == mAccessLayout || views[0] == mWidgetsLayout);
             
        // Clear the list if we're collecting main page views to avoid duplicates
        if (isCollectingMainViews) {
            viewList.clear();
        }

        // Add all the views if they're not already in the list
        for (View view : views) {
            if (view != null && !viewList.contains(view)) {
                viewList.add(view);
            }
        }

        // Log for debugging
        if (isCollectingMainViews) {
            android.util.Log.d("QsControlsView", "Collected " + viewList.size() + " main views");
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

    // Bluetooth and Internet connectivity methods
    private void toggleBluetoothState() {
        mBluetoothController.setBluetoothEnabled(!isBluetoothEnabled());
        updateBtState();
        post(() -> {
            updateBtState();
        });
    }
    
    private void updateConnectivityTiles() {
        updateBtState();
        updateInternetButtonState();
    }
    
    private void showBluetoothDialog(View view) {
        post(() -> 
            mBluetoothTileDialogViewModel.showDialog(Expandable.fromView(view)));
        VibrationUtils.triggerVibration(mContext, 2);
    }
    
    private void showInternetDialog(View view) {
        post(() -> mInternetDialogManager.create(true,
                mAccessPointController.canConfigMobileData(),
                mAccessPointController.canConfigWifi(), Expandable.fromView(view)));
        VibrationUtils.triggerVibration(mContext, 2);
    }
    
    private void updateTileButtonState(LaunchableLinearLayout tile, boolean active,
                    int activeResource, int inactiveResource, 
                    String activeString, String inactiveString) {
        post(new Runnable() {
            @Override
            public void run() {
                if (tile != null) {
                    ImageView tileIcon = null;
                    TextView tileLabel = null;
                    if (tile.getId() == R.id.internet_btn) {
                        tileIcon = tile.findViewById(R.id.internet_btn_icon);
                        tileLabel = tile.findViewById(R.id.internet_btn_text);
                    } else if (tile.getId() == R.id.bt_btn) {
                        tileIcon = tile.findViewById(R.id.bt_btn_icon);
                        tileLabel = tile.findViewById(R.id.bt_btn_text);
                    }
                    if (tileIcon != null && tileLabel != null) {
                        tileIcon.setImageDrawable(mContext.getDrawable(active ? activeResource : inactiveResource));
                        tileLabel.setText(active ? activeString : inactiveString);
                        setButtonActiveState(tile, active);
                    }
                }
            }
        });
    }
    
    private void setButtonActiveState(LaunchableLinearLayout tile, boolean active) {
        int bgTint = active ? mAccentColor : mBgColor;
        int tintColor = active ? mBgColor : mTintColor;
        
        if (tile != null) {
            ImageView tileIcon = null;
            ImageView chevron = null;
            TextView tileLabel = null;
            if (tile.getId() == R.id.internet_btn) {
                tileIcon = tile.findViewById(R.id.internet_btn_icon);
                tileLabel = tile.findViewById(R.id.internet_btn_text);
                chevron = tile.findViewById(R.id.internet_btn_arrow);
            } else if (tile.getId() == R.id.bt_btn) {
                tileIcon = tile.findViewById(R.id.bt_btn_icon);
                tileLabel = tile.findViewById(R.id.bt_btn_text);
                chevron = tile.findViewById(R.id.bt_btn_arrow);
            }
            
            if (chevron != null) {
                chevron.setImageTintList(ColorStateList.valueOf(tintColor));
            }
            if (tileIcon != null) {
                tileIcon.setImageTintList(ColorStateList.valueOf(tintColor));
            }
            if (tileLabel != null) {
                tileLabel.setTextColor(ColorStateList.valueOf(tintColor));
            }
            if (tile instanceof View) {
                ((View) tile).setBackgroundTintList(ColorStateList.valueOf(bgTint));
            }
        }
    }
    
    private void updateInternetButtonState() {
        if (mWifiSignalCallback == null || mCellSignalCallback == null) return;
        
        boolean wifiEnabled = mWifiSignalCallback.mInfo.enabled;
        boolean dataEnabled = mCellSignalCallback.mInfo.enabled;
        
        if (wifiEnabled) {
            updateWiFiButtonState();
        } else if (dataEnabled) {
            updateMobileDataState();
        } else {
            String inactiveString = mContext.getResources().getString(INTERNET_LABEL_INACTIVE);
            updateTileButtonState(mInternetButton, false, DATA_INACTIVE, DATA_INACTIVE, inactiveString, inactiveString);
        }
    }

    private void updateWiFiButtonState() {
        if (mWifiSignalCallback == null) return;
        final WifiCallbackInfo cbi = mWifiSignalCallback.mInfo;
        String inactiveString = mContext.getResources().getString(WIFI_LABEL_INACTIVE);
        updateTileButtonState(mInternetButton, true, 
            WIFI_ACTIVE, WIFI_INACTIVE, cbi.ssid != null ? removeDoubleQuotes(cbi.ssid) : inactiveString, inactiveString);
    }

    private void updateMobileDataState() {
        if (mNetworkController == null) return;
        String networkName = mNetworkController.getMobileDataNetworkName();
        boolean hasNetwork = !TextUtils.isEmpty(networkName) && mNetworkController.hasMobileDataFeature();
        String inactiveString = mContext.getResources().getString(DATA_LABEL_INACTIVE);
        updateTileButtonState(mInternetButton, true, 
            DATA_ACTIVE, DATA_INACTIVE, hasNetwork ? networkName : inactiveString, inactiveString);
    }
    
    private void updateBtState() {
        if (mBluetoothController == null) return;
        String deviceName = isBluetoothEnabled() ? mBluetoothController.getConnectedDeviceName() : "";
        boolean isConnected = !TextUtils.isEmpty(deviceName);
        String inactiveString = mContext.getResources().getString(BT_LABEL_INACTIVE);
        updateTileButtonState(mBtButton, isBluetoothEnabled(), 
            BT_ACTIVE, BT_INACTIVE, isConnected ? deviceName : inactiveString, inactiveString);
    }
    
    private boolean isBluetoothEnabled() {
        final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    private boolean isMobileDataEnabled() {
        try {
            return Settings.Global.getInt(mContext.getContentResolver(), 
                Settings.Global.MOBILE_DATA, 0) == 1;
        } catch (Exception e) {
            return false;
        }
    }    

    @Nullable
    private static String removeDoubleQuotes(String string) {
        if (string == null) return null;
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    protected static final class CellCallbackInfo {
        boolean enabled;
    }

    protected static final class WifiCallbackInfo {
        boolean enabled;
        @Nullable
        String ssid;
    }

    protected final class WifiSignalCallback implements SignalCallback {
        final WifiCallbackInfo mInfo = new WifiCallbackInfo();
        
        public WifiSignalCallback() {
            mInfo.enabled = false;
            mInfo.ssid = null;
        }
        
        @Override
        public void setWifiIndicators(@NonNull WifiIndicators indicators) {
            if (indicators.qsIcon == null) {
                mInfo.enabled = false;
                return;
            }
            mInfo.enabled = indicators.enabled;
            mInfo.ssid = indicators.description;
            updateInternetButtonState();
         }
     }
    
    private final class CellSignalCallback implements SignalCallback {
        final CellCallbackInfo mInfo = new CellCallbackInfo();
        @Override
        public void setMobileDataIndicators(@NonNull MobileDataIndicators indicators) {
            if (indicators.qsIcon == null) {
                mInfo.enabled = false;
                return;
            }
            mInfo.enabled = isMobileDataEnabled();
            updateInternetButtonState();
        }
        @Override
        public void setNoSims(boolean show, boolean simDetected) {
            mInfo.enabled = simDetected && isMobileDataEnabled();
            updateInternetButtonState();
        }
        @Override
        public void setIsAirplaneMode(@NonNull IconState icon) {
            mInfo.enabled = !icon.visible && isMobileDataEnabled();
            updateInternetButtonState();
        }
    }
    
    private enum MediaAction {
        TOGGLE_PLAYBACK,
        PLAY_PREVIOUS,
        PLAY_NEXT
    }
}
