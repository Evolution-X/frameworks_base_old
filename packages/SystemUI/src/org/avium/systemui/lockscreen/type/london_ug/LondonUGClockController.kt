package org.avium.systemui.lockscreen.type.london_ug

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
import kotlin.math.roundToInt

class LondonUGClockController(context: Context) : FrameLayout(context), ICustomLockScreenClock {
    
    private val clockView: LondonUGClockView = LondonUGClockView(context)
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
    
    private class LondonUGClockView(context: Context) : View(context) {
        private var timeStr = ""
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var digitBitmaps: Map<Char, Bitmap?> = emptyMap()
        
        private val scaleRatio: Float
            get() {
                val dm = resources.displayMetrics
                val sw = minOf(dm.widthPixels, dm.heightPixels) / dm.density
                return sw / 420f
            }
        
        private val scale: Float
            get() = (scaleRatio * 56f) / 32f
        
        private val padding: Float
            get() = resources.getDimension(R.dimen.clock_padding) * scaleRatio
        
        private val topMargin: Float
            get() = resources.getDimension(R.dimen.clock_center_date_margin_top) * scaleRatio
        
        init {
            setWillNotDraw(false)
            loadDigitBitmaps()
        }
        
        private fun loadDigitBitmaps() {
            val digitResIds = intArrayOf(
                R.drawable.london_ug_0,
                R.drawable.london_ug_1,
                R.drawable.london_ug_2,
                R.drawable.london_ug_3,
                R.drawable.london_ug_4,
                R.drawable.london_ug_5,
                R.drawable.london_ug_6,
                R.drawable.london_ug_7,
                R.drawable.london_ug_8,
                R.drawable.london_ug_9
            )
            
            val bitmaps = digitResIds.map { resId ->
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
            
            digitBitmaps = ('0'..'9').zip(bitmaps).toMap()
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
            
            var totalWidth = 0f
            timeStr.forEachIndexed { index, char ->
                val bitmap = digitBitmaps[char]
                if (bitmap != null) {
                    totalWidth += bitmap.width * scale
                    if (index < timeStr.lastIndex) totalWidth += padding
                }
            }
            
            if (totalWidth <= 0f) return
            
            val availableWidth = width.toFloat()
            val finalWidth = kotlin.math.min(totalWidth, availableWidth)
            val startX = (availableWidth - finalWidth) / 2f
            val centerY = (height / 2f) + topMargin
            
            // Semi-transparent black for light theme, white for dark
            val color = Color.argb((0.6f * 255).roundToInt(), 0, 0, 0)
            paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            
            var x = startX
            timeStr.forEachIndexed { index, char ->
                val bitmap = digitBitmaps[char]
                if (bitmap != null) {
                    val matrix = Matrix().apply {
                        postScale(scale, scale)
                        postTranslate(x, centerY - (bitmap.height * scale / 2))
                    }
                    canvas.drawBitmap(bitmap, matrix, paint)
                    x += bitmap.width * scale
                    if (index < timeStr.lastIndex) x += padding
                }
            }
        }
    }
}
