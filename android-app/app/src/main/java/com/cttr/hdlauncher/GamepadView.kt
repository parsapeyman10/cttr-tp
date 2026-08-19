package com.cttr.hdlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * A fully touch-driven on-screen gamepad (mobile gamepad / virtual controller).
 *
 * Layout (default, right-handed):
 *  - Left side : analog stick (multi-touch, draggable thumb, dead-zone free)
 *  - Right side: face buttons (Triangle / Cross / Square / Circle) in a diamond
 *  - Top       : L1/R1 and L2/R2 shoulder buttons
 *  - Bottom    : Start / Select
 *
 * Supports opacity tweaking and left/right hand flip. Emits a [GamepadState]
 * via [onStateChanged] and is also pollable through [getState].
 */
class GamepadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---- Configuration ----
    var opacity: Float = 0.55f
        set(value) {
            field = value.coerceIn(0.15f, 1f)
            invalidate()
        }

    var flipSides: Boolean = false
        set(value) {
            field = value
            computeLayout()
            invalidate()
        }

    var onStateChanged: ((GamepadState) -> Unit)? = null

    // ---- Paints ----
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- Layout (px) ----
    private var W = 0f
    private var H = 0f
    private var stickCx = 0f
    private var stickCy = 0f
    private var stickR = 0f
    private var thumbR = 0f
    private var faceCx = 0f
    private var faceCy = 0f
    private var faceR = 0f
    private var btnL1 = RectF()
    private var btnR1 = RectF()
    private var btnL2 = RectF()
    private var btnR2 = RectF()
    private var startC = android.graphics.PointF()
    private var selectC = android.graphics.PointF()
    private var menuR = 0f

    // ---- State ----
    private val pointers = HashMap<Int, Control>()
    private var stickPointerId = -1
    private var stickX = 0f
    private var stickY = 0f
    private val activeButtons = HashSet<GameButton>()
    private var _state = GamepadState()

    private val vibrator = getVibrator()

    private sealed class Control {
        object Stick : Control()
        data class Btn(val button: GameButton) : Control()
    }

    init {
        basePaint.style = Paint.Style.FILL
        thumbPaint.style = Paint.Style.FILL
        thumbPaint.color = Color.parseColor("#FF8C00")
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        W = w.toFloat()
        H = h.toFloat()
        computeLayout()
    }

    private fun computeLayout() {
        val left = if (!flipSides) 0.20f else 0.80f
        val right = if (!flipSides) 0.80f else 0.20f

        stickR = H * 0.22f
        thumbR = stickR * 0.42f
        stickCx = W * left
        stickCy = H * 0.60f
        stickX = stickCx
        stickY = stickCy

        faceR = H * 0.075f
        faceCx = W * right
        faceCy = H * 0.58f

        val shW = W * 0.16f
        val shH = H * 0.085f
        val shY1 = H * 0.13f
        val shY2 = H * 0.27f
        val lx = W * 0.26f
        val rx = W * 0.74f
        btnL1 = RectF(lx - shW / 2, shY1 - shH / 2, lx + shW / 2, shY1 + shH / 2)
        btnR1 = RectF(rx - shW / 2, shY1 - shH / 2, rx + shW / 2, shY1 + shH / 2)
        btnL2 = RectF(lx - shW / 2, shY2 - shH / 2, lx + shW / 2, shY2 + shH / 2)
        btnR2 = RectF(rx - shW / 2, shY2 - shH / 2, rx + shW / 2, shY2 + shH / 2)

        menuR = H * 0.045f
        startC = android.graphics.PointF(W * 0.46f, H * 0.90f)
        selectC = android.graphics.PointF(W * 0.54f, H * 0.90f)
    }

    // ---- Touch handling ----
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                assign(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    if (pointers[pid] is Control.Stick) {
                        updateStick(event.getX(i), event.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                release(event.getPointerId(event.actionIndex))
            }
        }
        recompute()
        invalidate()
        return true
    }

    private fun assign(pid: Int, x: Float, y: Float) {
        if (stickPointerId == -1 && dist(x, y, stickCx, stickCy) <= stickR * 1.15f) {
            stickPointerId = pid
            pointers[pid] = Control.Stick
            updateStick(x, y)
            return
        }

        val dUp = dist(x, y, faceCx, faceCy - faceR * 1.6f)
        val dDown = dist(x, y, faceCx, faceCy + faceR * 1.6f)
        val dLeft = dist(x, y, faceCx - faceR * 1.6f, faceCy)
        val dRight = dist(x, y, faceCx + faceR * 1.6f, faceCy)
        val faceMin = minOf(dUp, dDown, dLeft, dRight)
        if (faceMin <= faceR * 1.5f) {
            val btn = when (faceMin) {
                dUp -> GameButton.TRIANGLE
                dDown -> GameButton.CROSS
                dLeft -> GameButton.SQUARE
                else -> GameButton.CIRCLE
            }
            pointers[pid] = Control.Btn(btn)
            activeButtons.add(btn)
            haptic()
            return
        }

        if (btnL1.contains(x, y)) { press(pid, GameButton.L1); return }
        if (btnR1.contains(x, y)) { press(pid, GameButton.R1); return }
        if (btnL2.contains(x, y)) { press(pid, GameButton.L2); return }
        if (btnR2.contains(x, y)) { press(pid, GameButton.R2); return }
        if (dist(x, y, startC.x, startC.y) <= menuR * 1.4f) { press(pid, GameButton.START); return }
        if (dist(x, y, selectC.x, selectC.y) <= menuR * 1.4f) { press(pid, GameButton.SELECT); return }
    }

    private fun press(pid: Int, btn: GameButton) {
        pointers[pid] = Control.Btn(btn)
        activeButtons.add(btn)
        haptic()
    }

    private fun updateStick(x: Float, y: Float) {
        var dx = x - stickCx
        var dy = y - stickCy
        val d = hypot(dx, dy)
        if (d > stickR) {
            dx = dx / d * stickR
            dy = dy / d * stickR
        }
        stickX = stickCx + dx
        stickY = stickCy + dy
    }

    private fun release(pid: Int) {
        val ctrl = pointers.remove(pid) ?: return
        if (ctrl is Control.Stick) {
            stickPointerId = -1
            stickX = stickCx
            stickY = stickCy
        } else if (ctrl is Control.Btn) {
            val stillHeld = pointers.values.any { it is Control.Btn && it.button == ctrl.button }
            if (!stillHeld) activeButtons.remove(ctrl.button)
        }
    }

    private fun recompute() {
        val lx = if (stickPointerId == -1) 0f else ((stickX - stickCx) / stickR).coerceIn(-1f, 1f)
        val ly = if (stickPointerId == -1) 0f else ((stickY - stickCy) / stickR).coerceIn(-1f, 1f)
        _state = GamepadState(leftX = lx, leftY = ly, buttons = HashSet(activeButtons))
        onStateChanged?.invoke(_state)
    }

    fun getState(): GamepadState = _state

    private fun haptic() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12)
            }
        } catch (_: Exception) {
        }
    }

    // ---- Drawing ----
    override fun onDraw(canvas: Canvas) {
        val a = (opacity * 255).toInt()

        // Stick base + thumb
        basePaint.color = Color.argb((a * 0.5f).toInt(), 30, 30, 40)
        canvas.drawCircle(stickCx, stickCy, stickR, basePaint)
        basePaint.color = Color.argb(a, 80, 80, 100)
        canvas.drawCircle(stickCx, stickCy, stickR * 0.92f, basePaint)
        thumbPaint.alpha = a
        canvas.drawCircle(stickX, stickY, thumbR, thumbPaint)

        drawFace(canvas, faceCx, faceCy - faceR * 1.6f, GameButton.TRIANGLE, "Y")
        drawFace(canvas, faceCx, faceCy + faceR * 1.6f, GameButton.CROSS, "X")
        drawFace(canvas, faceCx - faceR * 1.6f, faceCy, GameButton.SQUARE, "□")
        drawFace(canvas, faceCx + faceR * 1.6f, faceCy, GameButton.CIRCLE, "O")

        drawShoulder(canvas, btnL1, GameButton.L1, "L1")
        drawShoulder(canvas, btnR1, GameButton.R1, "R1")
        drawShoulder(canvas, btnL2, GameButton.L2, "L2")
        drawShoulder(canvas, btnR2, GameButton.R2, "R2")

        drawMenu(canvas, startC.x, startC.y, GameButton.START, "ST")
        drawMenu(canvas, selectC.x, selectC.y, GameButton.SELECT, "SE")
    }

    private fun drawFace(canvas: Canvas, cx: Float, cy: Float, btn: GameButton, label: String) {
        val pressed = activeButtons.contains(btn)
        basePaint.color = if (pressed) Color.argb((opacity * 255).toInt(), 255, 180, 40)
        else Color.argb((opacity * 200).toInt(), 40, 40, 55)
        canvas.drawCircle(cx, cy, faceR, basePaint)
        textPaint.color = Color.argb(255, 255, 255, 255)
        textPaint.textSize = faceR * 1.1f
        canvas.drawText(label, cx, cy + faceR * 0.38f, textPaint)
    }

    private fun drawShoulder(canvas: Canvas, r: RectF, btn: GameButton, label: String) {
        val pressed = activeButtons.contains(btn)
        basePaint.color = if (pressed) Color.argb((opacity * 255).toInt(), 255, 180, 40)
        else Color.argb((opacity * 200).toInt(), 40, 40, 55)
        canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, basePaint)
        textPaint.color = Color.argb(255, 255, 255, 255)
        textPaint.textSize = r.height() * 0.5f
        canvas.drawText(label, r.centerX(), r.centerY() + r.height() * 0.18f, textPaint)
    }

    private fun drawMenu(canvas: Canvas, cx: Float, cy: Float, btn: GameButton, label: String) {
        val pressed = activeButtons.contains(btn)
        basePaint.color = if (pressed) Color.argb((opacity * 255).toInt(), 255, 180, 40)
        else Color.argb((opacity * 200).toInt(), 40, 40, 55)
        canvas.drawCircle(cx, cy, menuR, basePaint)
        textPaint.color = Color.argb(255, 255, 255, 255)
        textPaint.textSize = menuR * 0.7f
        canvas.drawText(label, cx, cy + menuR * 0.28f, textPaint)
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) = hypot(x1 - x2, y1 - y2)
}
