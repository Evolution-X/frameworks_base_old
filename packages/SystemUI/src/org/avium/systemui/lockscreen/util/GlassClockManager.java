package org.avium.systemui.lockscreen.util;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.graphics.Color;

import java.io.IOException;

public class GlassClockManager {

    private final Context mContext;
    private Bitmap mBlurredWallpaperBitmap;
    private final DigitView[] mDigitViews;
    private final int[] mDigitResources;

    public GlassClockManager(Context context, int numDigits, int[] digitResources) {
        this.mContext = context;
        this.mDigitResources = digitResources;

        this.mDigitViews = new DigitView[numDigits];
        for (int i = 0; i < numDigits; i++) {
            mDigitViews[i] = new DigitView(context);
        }
    }

    public View[] getDigitViews() {
        return mDigitViews;
    }

    public void prepareWallpaper() {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
        Bitmap wallpaperBitmap = null;

        try (ParcelFileDescriptor pfd = wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_LOCK)) {
            if (pfd != null) {
                wallpaperBitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
            }
        } catch (IOException e) {
        }
        
        if (wallpaperBitmap == null) {
            try (ParcelFileDescriptor pfd = wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)) {
                if (pfd != null) {
                    wallpaperBitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                }
            } catch (IOException e) {
            }
        }

        if (wallpaperBitmap != null) {
            mBlurredWallpaperBitmap = blurBitmap(wallpaperBitmap, 25f);
        }

        for (DigitView digitView : mDigitViews) {
            digitView.invalidate();
        }
    }

    public void updateTime(String timeString) {
        if (timeString == null || timeString.length() != 4 || mDigitViews.length != 4) return;

        int h1 = Character.getNumericValue(timeString.charAt(0));
        int h2 = Character.getNumericValue(timeString.charAt(1));
        int m1 = Character.getNumericValue(timeString.charAt(2));
        int m2 = Character.getNumericValue(timeString.charAt(3));

        mDigitViews[0].setDigitDrawable(ContextCompat.getDrawable(mContext, mDigitResources[h1]));
        mDigitViews[1].setDigitDrawable(ContextCompat.getDrawable(mContext, mDigitResources[h2]));
        mDigitViews[2].setDigitDrawable(ContextCompat.getDrawable(mContext, mDigitResources[m1]));
        mDigitViews[3].setDigitDrawable(ContextCompat.getDrawable(mContext, mDigitResources[m2]));
    }

    public void cleanup() {
        if (mBlurredWallpaperBitmap != null && !mBlurredWallpaperBitmap.isRecycled()) {
            mBlurredWallpaperBitmap.recycle();
        }
        mBlurredWallpaperBitmap = null;
    }

    private Bitmap blurBitmap(Bitmap input, float radius) {
        if (input == null || input.isRecycled()) {
            return null;
        }

        try {
            float scaleFactor = 5.0f / radius;
            int newWidth = Math.max(1, (int)(input.getWidth() * scaleFactor));
            int newHeight = Math.max(1, (int)(input.getHeight() * scaleFactor));
            Bitmap smallBitmap = Bitmap.createScaledBitmap(input, newWidth, newHeight, true);
            RenderScript rs = RenderScript.create(mContext);
            Allocation input_alloc = Allocation.createFromBitmap(rs, smallBitmap);
            Allocation output_alloc = Allocation.createTyped(rs, input_alloc.getType());
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            script.setInput(input_alloc);
            script.setRadius(25.0f);
            script.forEach(output_alloc);
            Bitmap blurred = Bitmap.createBitmap(smallBitmap.getWidth(), smallBitmap.getHeight(), smallBitmap.getConfig());
            output_alloc.copyTo(blurred);
            input_alloc.destroy();
            output_alloc.destroy();
            script.destroy();
            rs.destroy();
            Bitmap finalResult = Bitmap.createScaledBitmap(blurred, input.getWidth(), input.getHeight(), true);
            smallBitmap.recycle();
            blurred.recycle();

            return finalResult;
        } catch (Exception e) {
            return input;
        }
    }

    public class DigitView extends View {

        private Drawable mDigitDrawable;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mXfermodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private Bitmap mMaskBitmap;
        private Canvas mMaskCanvas;

        public DigitView(Context context) {
            super(context);
            mXfermodePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }

        public void setDigitDrawable(@Nullable Drawable digitDrawable) {
            this.mDigitDrawable = digitDrawable;
            updateMask();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w > 0 && h > 0) {
                if (mMaskBitmap != null && !mMaskBitmap.isRecycled()) {
                    mMaskBitmap.recycle();
                }
                mMaskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                mMaskCanvas = new Canvas(mMaskBitmap);
                updateMask();
            }
        }

        private void updateMask() {
            if (mMaskCanvas != null && mDigitDrawable != null) {
                mMaskCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                mDigitDrawable.setBounds(0, 0, getWidth(), getHeight());
                mDigitDrawable.draw(mMaskCanvas);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Bitmap wallpaper = GlassClockManager.this.mBlurredWallpaperBitmap;

            if (wallpaper == null || mDigitDrawable == null || mMaskBitmap == null) {
                return;
            }

            int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            RectF viewRect = new RectF(0, 0, getWidth(), getHeight());
            int[] location = new int[2];
            getLocationOnScreen(location);

            float scaleX = (float) wallpaper.getWidth() / getResources().getDisplayMetrics().widthPixels;
            float scaleY = (float) wallpaper.getHeight() / getResources().getDisplayMetrics().heightPixels;

            Rect srcRect = new Rect(
                (int) (location[0] * scaleX),
                (int) (location[1] * scaleY),
                (int) ((location[0] + getWidth()) * scaleX),
                (int) ((location[1] + getHeight()) * scaleY)
            );

            srcRect.left = Math.max(0, Math.min(srcRect.left, wallpaper.getWidth()));
            srcRect.top = Math.max(0, Math.min(srcRect.top, wallpaper.getHeight()));
            srcRect.right = Math.max(srcRect.left, Math.min(srcRect.right, wallpaper.getWidth()));
            srcRect.bottom = Math.max(srcRect.top, Math.min(srcRect.bottom, wallpaper.getHeight()));

            canvas.drawBitmap(wallpaper, srcRect, viewRect, mPaint);
            Paint maskPaint = new Paint();
            maskPaint.setColor(Color.WHITE);
            maskPaint.setAlpha(50);
            canvas.drawRect(viewRect, maskPaint);
            canvas.drawBitmap(mMaskBitmap, 0, 0, mXfermodePaint);

            canvas.restoreToCount(saveCount);
        }
    }
}