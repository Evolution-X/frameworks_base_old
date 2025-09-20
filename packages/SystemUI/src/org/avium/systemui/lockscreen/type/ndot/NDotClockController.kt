package org.avium.systemui.lockscreen.type.ndot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.android.systemui.customization.R
import org.avium.systemui.lockscreen.ICustomLockScreenClock
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

class NDotClockController(context: Context) : FrameLayout(context), ICustomLockScreenClock {
    
    private val clockView: NDotClockView = NDotClockView(context)
    private val calendar = Calendar.getInstance()
    private val helper = TimeTickHelper()
    
    init {
        addView(clockView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))
        helper.registerTimeReceiver(context) { onTimeTick() }
        updateTime()
    }
    
    override fun getView(context: Context?): View = this
    
    override fun onTimeTick() {
        updateTime()
    }
    
    private fun updateTime() {
        calendar.timeInMillis = System.currentTimeMillis()
        val format = if (DateFormat.is24HourFormat(context)) "HHmm" else "hmm"
        val timeStr = SimpleDateFormat(format, Locale.getDefault()).format(calendar.time)
        clockView.setTime(timeStr)
    }
    
    override fun onNotificationStateChanged(hasNotifications: Boolean) {}
    override fun applyStyles() {}
    
    override fun onDestroy() {
        helper.unregisterTimeReceiver()
    }
    
    private inner class TimeTickHelper {
        private var receiver: BroadcastReceiver? = null
        private var receiverContext: Context? = null
        
        fun registerTimeReceiver(context: Context, onTick: () -> Unit) {
            if (receiver != null) return
            
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    onTick()
                }
            }
            
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            
            context.registerReceiver(receiver, filter)
            receiverContext = context
        }
        
        fun unregisterTimeReceiver() {
            receiver?.let { rec ->
                receiverContext?.unregisterReceiver(rec)
                receiver = null
                receiverContext = null
            }
        }
    }
    
    private class NDotClockView(context: Context) : View(context) {
        private var timeStr = ""
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var normalBitmaps: List<Bitmap?> = emptyList()
        private var lightBitmaps: List<Bitmap?> = emptyList()
        
        private val scaleRatio: Float
            get() {
                val dm = resources.displayMetrics
                val sw = minOf(dm.widthPixels, dm.heightPixels) / dm.density
                return sw / 420f
            }
        
        private val clockPadding: Float
            get() = resources.getDimension(R.dimen.clock_padding) * scaleRatio
        
        private val overlapPadding: Float
            get() = resources.getDimension(R.dimen.overlap_padding) * scaleRatio
        
        private val topMargin: Float
            get() = resources.getDimension(R.dimen.clock_center_date_margin_top) * scaleRatio
        
        private val yOffset: Float
            get() = resources.getDimension(R.dimen.ndot_clock_offset) * scaleRatio
        
        init {
            setWillNotDraw(false)
            loadDigitBitmaps()
        }
        
        private fun loadDigitBitmaps() {
            val normalResIds = intArrayOf(
                R.drawable.ndot_0, R.drawable.ndot_1, R.drawable.ndot_2,
                R.drawable.ndot_3, R.drawable.ndot_4, R.drawable.ndot_5,
                R.drawable.ndot_6, R.drawable.ndot_7, R.drawable.ndot_8,
                R.drawable.ndot_9
            )
            
            val lightResIds = intArrayOf(
                R.drawable.ndot_0_light, R.drawable.ndot_1_light, R.drawable.ndot_2_light,
                R.drawable.ndot_3_light, R.drawable.ndot_4_light, R.drawable.ndot_5_light,
                R.drawable.ndot_6_light, R.drawable.ndot_7_light, R.drawable.ndot_8_light,
                R.drawable.ndot_9_light
            )
            
            normalBitmaps = loadBitmaps(normalResIds)
            lightBitmaps = loadBitmaps(lightResIds)
        }
        
        private fun loadBitmaps(resIds: IntArray): List<Bitmap?> {
            return resIds.map { resId ->
                try {
                    ContextCompat.getDrawable(context, resId)?.let { drawable ->
                        Bitmap.createBitmap(
                            drawable.intrinsicWidth,
                            drawable.intrinsicHeight,
                            Bitmap.Config.ARGB_8888
                        ).also { bitmap ->
                            val canvas = Canvas(bitmap)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
        
        fun setTime(time: String) {
            if (timeStr != time) {
                timeStr = time
                invalidate()
            }
        }
        
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val heightDimen = resources.getDimension(R.dimen.center_clock_height) * scaleRatio
            setMeasuredDimension(width, heightDimen.toInt())
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            if (timeStr.isEmpty() || !TextUtils.isDigitsOnly(timeStr)) return
            
            val length = timeStr.length
            if (length != 3 && length != 4) return
            
            val drawMask = when (length) {
                3 -> booleanArrayOf(false, true, true)
                4 -> booleanArrayOf(false, false, true, true)
                else -> return
            }
            
            val spacing = calculateSpacing(timeStr, clockPadding, overlapPadding)
            
            var totalWidth = 0f
            for (i in timeStr.indices) {
                val bmp = getDigitBitmap(timeStr[i], drawMask[i]) ?: continue
                totalWidth += bmp.width * scaleRatio
                if (i < spacing.size) totalWidth += spacing[i]
            }
            
            if (totalWidth <= 0f) return
            
            val availableWidth = min(width.toFloat(), totalWidth)
            val startX = (width - availableWidth) / 2f
            
            paint.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            
            var x = startX
            for (i in timeStr.indices) {
                val bmp = getDigitBitmap(timeStr[i], drawMask[i]) ?: continue
                val scale = scaleRatio
                val y = (height - bmp.height * scale) / 2f - yOffset + topMargin
                
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(x, y)
                }
                
                canvas.drawBitmap(bmp, matrix, paint)
                x += bmp.width * scale + if (i < spacing.size) spacing[i] else 0f
            }
        }
        
        private fun getDigitBitmap(char: Char, isLight: Boolean): Bitmap? {
            val index = char.digitToIntOrNull() ?: return null
            return if (isLight) {
                lightBitmaps.getOrNull(index)
            } else {
                normalBitmaps.getOrNull(index)
            }
        }
        
        private fun calculateSpacing(timeStr: String, padding: Float, overlap: Float): FloatArray {
            return when (timeStr.length) {
                3 -> {
                    val secondHalf = timeStr.substring(1)
                    floatArrayOf(padding, if ('1' !in secondHalf) overlap else padding)
                }
                4 -> {
                    val firstHalf = timeStr.substring(0, 2)
                    val secondHalf = timeStr.substring(2)
                    floatArrayOf(
                        if ('1' in firstHalf) padding else overlap,
                        padding,
                        if ('1' !in secondHalf) overlap else padding
                    )
                }
                else -> floatArrayOf()
            }
        }
    }
}
