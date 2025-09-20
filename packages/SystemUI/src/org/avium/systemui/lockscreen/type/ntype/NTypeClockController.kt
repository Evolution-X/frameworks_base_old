package org.avium.systemui.lockscreen.type.ntype

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

class NTypeClockController(context: Context) : FrameLayout(context), ICustomLockScreenClock {
    
    private val clockView: NTypeClockView = NTypeClockView(context)
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
    
    private class NTypeClockView(context: Context) : View(context) {
        private var timeStr = ""
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var digitBitmaps: List<Bitmap?> = emptyList()
        
        private val scaleRatio: Float
            get() {
                val dm = resources.displayMetrics
                val sw = minOf(dm.widthPixels, dm.heightPixels) / dm.density
                return sw / 420f
            }
        
        init {
            setWillNotDraw(false)
            loadDigitBitmaps()
        }
        
        private fun loadDigitBitmaps() {
            val resIds = intArrayOf(
                R.drawable.ntype_0, R.drawable.ntype_1, R.drawable.ntype_2,
                R.drawable.ntype_3, R.drawable.ntype_4, R.drawable.ntype_5,
                R.drawable.ntype_6, R.drawable.ntype_7, R.drawable.ntype_8,
                R.drawable.ntype_9
            )
            digitBitmaps = resIds.map { resId ->
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
            val height = (resources.displayMetrics.density * 200).toInt()
            setMeasuredDimension(width, height)
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            if (timeStr.isEmpty() || !TextUtils.isDigitsOnly(timeStr)) return
            
            val padding = 8f * resources.displayMetrics.density
            var totalWidth = 0f
            
            timeStr.forEachIndexed { index, char ->
                digitBitmaps.getOrNull(char.digitToIntOrNull() ?: return)?.let { bmp ->
                    totalWidth += bmp.width * scaleRatio
                    if (index < timeStr.lastIndex) {
                        totalWidth += padding
                    }
                }
            }
            
            if (timeStr.length >= 3) {
                totalWidth += 2 * padding * 2
            }
            
            if (totalWidth <= 0f) return
            
            val availableWidth = min(totalWidth, width.toFloat())
            val startX = (width - availableWidth) / 2f
            val centerY = height / 2f
            
            paint.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            
            var currentX = startX
            
            timeStr.forEachIndexed { index, char ->
                val bitmap = digitBitmaps.getOrNull(char.digitToIntOrNull() ?: return@forEachIndexed)
                    ?: return@forEachIndexed
                
                val yOffset = centerY - (bitmap.height * scaleRatio / 2f)
                
                val matrix = Matrix().apply {
                    postScale(scaleRatio, scaleRatio)
                    postTranslate(currentX, yOffset)
                }
                
                canvas.drawBitmap(bitmap, matrix, paint)
                currentX += bitmap.width * scaleRatio + padding
                
                if (timeStr.length - index == 3) {
                    val dotSize = 8f * resources.displayMetrics.density
                    val dotCenterX = currentX + padding
                    val dotRadius = dotSize / 2
                    val topDotY = centerY - padding / 2
                    val bottomDotY = centerY + padding / 2
                    
                    canvas.drawCircle(dotCenterX, topDotY, dotRadius, paint)
                    canvas.drawCircle(dotCenterX, bottomDotY, dotRadius, paint)
                    
                    currentX = dotCenterX + padding
                }
            }
        }
    }
}
