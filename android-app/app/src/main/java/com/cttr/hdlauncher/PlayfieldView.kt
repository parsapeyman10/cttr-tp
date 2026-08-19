package com.cttr.hdlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Simple demo playfield that uses the bundled Crash Tag Team Racing textures
 * and is driven by the on-screen [GamepadView]. Proves that the mobile gamepad
 * actually controls something on screen.
 */
class PlayfieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bg: Bitmap? = null
    private var kart: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var x = 0f
    private var y = 0f
    private var angle = 0f

    var gamepadProvider: (() -> GamepadState)? = null

    init {
        try {
            val am = context.assets
            bg = BitmapFactory.decodeStream(am.open("cttr/menu/main_bg.png"))
            kart = BitmapFactory.decodeStream(am.open("cttr/chars/kart.png"))?.let {
                val w = (it.width * 0.5f).toInt().coerceAtLeast(1)
                val h = (it.height * 0.5f).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(it, w, h, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (x == 0f && y == 0f) {
            x = w / 2f
            y = h / 2f
        }
    }

    /** Advance simulation by dt seconds using the latest gamepad state. */
    fun update(dt: Float) {
        val s = gamepadProvider?.invoke() ?: GamepadState()
        val ax = s.leftX
        val ay = s.leftY
        val boost = if (s.isPressed(GameButton.CROSS) || s.isPressed(GameButton.R2)) 1.8f else 1f
        val brake = if (s.isPressed(GameButton.SQUARE) || s.isPressed(GameButton.L2)) 0.4f else 1f
        val speed = 600f * boost * brake

        x += ax * speed * dt
        y += ay * speed * dt

        val w = width.toFloat()
        val h = height.toFloat()
        x = x.coerceIn(40f, (w - 40f).coerceAtLeast(40f))
        y = y.coerceIn(40f, (h - 40f).coerceAtLeast(40f))

        if (hypot(ax, ay) > 0.05f) {
            angle = atan2(ay.toDouble(), ax.toDouble()).toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height

        bg?.let {
            canvas.drawBitmap(it, Rect(0, 0, it.width, it.height), Rect(0, 0, w, h), paint)
        } ?: run {
            canvas.drawColor(Color.DKGRAY)
        }

        kart?.let {
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat())
            val dw = it.width * 1.2f
            val dh = it.height * 1.2f
            canvas.drawBitmap(
                it,
                Rect(0, 0, it.width, it.height),
                RectF(-dw / 2, -dh / 2, dw / 2, dh / 2),
                paint
            )
            canvas.restore()
        }
    }
}
