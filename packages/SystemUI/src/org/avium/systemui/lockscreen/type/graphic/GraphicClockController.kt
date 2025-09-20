package org.avium.systemui.lockscreen.type.graphic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.text.TextUtils
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.android.systemui.customization.R
import org.avium.systemui.lockscreen.ICustomLockScreenClock
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class GraphicClockController(context: Context) : FrameLayout(context), ICustomLockScreenClock {
    
    private val clockView: GraphicClockView = GraphicClockView(context)
    private val calendar = Calendar.getInstance()
    private val helper = TimeTickHelper()
    
    init {
        addView(clockView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dpToPx(context, 10)
        })
        helper.registerTimeReceiver(context) { onTimeTick() }
        updateTime()
    }
    
    override fun getView(context: Context?): View = this
    
    override fun onTimeTick() {
        updateTime()
    }
    
    private fun updateTime() {
        calendar.timeInMillis = System.currentTimeMillis()
        val format = "HHmmss"
        val timeStr = SimpleDateFormat(format, Locale.getDefault()).format(calendar.time)
        clockView.setTime(timeStr)
    }
    
    override fun onNotificationStateChanged(hasNotifications: Boolean) {}
    override fun applyStyles() {}
    
    override fun onDestroy() {
        helper.unregisterTimeReceiver()
        clockView.stopUpdating()
    }
    
    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
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
    
    private class GraphicClockView(context: Context) : View(context) {
        private var timeStr = ""
        private val updateRunnable = object : Runnable {
            override fun run() {
                val cal = Calendar.getInstance()
                val format = SimpleDateFormat("HHmmss", Locale.getDefault())
                val currentTime = format.format(cal.time)
                setTime(currentTime)
                postDelayed(this, 1000)
            }
        }
        
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            style = Paint.Style.FILL 
        }
        
        private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
        }
        
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            style = Paint.Style.FILL 
        }
        
        private var tickBitmap: Bitmap? = null
        private var tickLightBitmap: Bitmap? = null
        
        private val scaleRatio: Float
            get() {
                val dm = resources.displayMetrics
                val sw = minOf(dm.widthPixels, dm.heightPixels) / dm.density
                return sw / 420f
            }
        
        private val dotSize: Float
            get() = resources.getDimension(R.dimen.dot_size) * scaleRatio
        
        private val handSize: Float
            get() = resources.getDimension(R.dimen.clock_hand_size) * scaleRatio
        
        init {
            setWillNotDraw(false)
            loadTickBitmaps()
        }
        
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            startUpdating()
        }
        
        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopUpdating()
        }
        
        private fun startUpdating() {
            removeCallbacks(updateRunnable)
            post(updateRunnable)
        }
        
        fun stopUpdating() {
            removeCallbacks(updateRunnable)
        }
        
        private fun loadTickBitmaps() {
            try {
                tickBitmap = loadBitmap(R.drawable.graphic_tick)
                tickLightBitmap = loadBitmap(R.drawable.graphic_tick_light)
            } catch (e: Exception) {
            }
        }
        
        private fun loadBitmap(resId: Int): Bitmap? {
            return try {
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
        
        fun setTime(time: String) {
            if (timeStr != time) {
                timeStr = time
                invalidate()
            }
        }
        
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val heightDimen = resources.getDimension(R.dimen.clock_height) * scaleRatio
            setMeasuredDimension(width, heightDimen.toInt())
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            val currentTime = if (timeStr.isEmpty()) {
                val cal = Calendar.getInstance()
                SimpleDateFormat("HHmmss", Locale.getDefault()).format(cal.time)
            } else {
                timeStr
            }
            
            drawClockTicks(canvas)
            
            drawClockDot(canvas, dotSize * 1.5f, Color.WHITE)
            
            if (currentTime.isNotEmpty() && TextUtils.isDigitsOnly(currentTime)) {
                drawClockHands(canvas, currentTime)
            }
            
            val redColor = ContextCompat.getColor(context, R.color.clock_dot_color)
            drawClockDot(canvas, dotSize, redColor)
        }
        
        private fun drawClockDot(canvas: Canvas, size: Float, color: Int) {
            dotPaint.color = color
            dotPaint.xfermode = null
            val cx = width / 2f
            val cy = height / 2f
            val radius = size / 2f
            canvas.drawOval(RectF(cx - radius, cy - radius, cx + radius, cy + radius), dotPaint)
        }
        
        private fun drawClockHands(canvas: Canvas, time: String) {
            try {
                val seconds = if (time.length >= 2) time.takeLast(2).toInt() else 0
                val minutes = if (time.length >= 4) time.dropLast(2).takeLast(2).toInt() else 0
                val hours = if (time.length >= 5) time.dropLast(4).toInt() % 12 else 0
                
                val baseColor = Color.WHITE
                val highlightColor = ContextCompat.getColor(context, R.color.clock_dot_color)
                
                val hourAngle = (hours + minutes / 60f) * 5f
                val minuteAngle = minutes + seconds / 60f
                val secondAngle = seconds.toFloat()
                
                drawHand(canvas, hourAngle, handSize * 2, 0.22f to 0.38f, baseColor)
                
                drawHand(canvas, minuteAngle, handSize, 0.22f to 0.42f, baseColor)
                
                drawHand(canvas, secondAngle, handSize / 2, 0.19f to 0.42f, highlightColor)
            } catch (e: NumberFormatException) {
            }
        }
        
        private fun drawHand(
            canvas: Canvas,
            position: Float,
            thickness: Float,
            multipliers: Pair<Float, Float>,
            color: Int
        ) {
            val centerX = width / 2f
            val centerY = height / 2f
            val angleRad = Math.toRadians((position * 6 - 90).toDouble())
            
            val startLength = multipliers.first * height
            val endLength = multipliers.second * height
            
            val startX = centerX - cos(angleRad) * startLength
            val startY = centerY - sin(angleRad) * startLength
            val endX = centerX + cos(angleRad) * endLength
            val endY = centerY + sin(angleRad) * endLength
            
            handPaint.color = color
            handPaint.strokeWidth = thickness
            
            canvas.drawLine(
                startX.toFloat(), startY.toFloat(),
                endX.toFloat(), endY.toFloat(),
                handPaint
            )
        }
        
        private fun drawClockTicks(canvas: Canvas) {
            val tick = tickBitmap ?: return
            val tickLight = tickLightBitmap ?: return
            
            val color = Color.WHITE
            tickPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            
            val tickWidth = tick.width * scaleRatio
            val tickHeight = tick.height * scaleRatio
            val left = (width - tickWidth) / 2f
            val top = (height - tickHeight) / 2f
            
            val matrix = Matrix().apply {
                postScale(scaleRatio, scaleRatio)
                postTranslate(left, top)
            }
            
            canvas.drawBitmap(tickLight, matrix, tickPaint)
            
            canvas.drawBitmap(tick, matrix, tickPaint)
        }
    }
}
