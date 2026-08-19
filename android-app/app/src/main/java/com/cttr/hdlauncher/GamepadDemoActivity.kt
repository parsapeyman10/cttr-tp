package com.cttr.hdlauncher

import android.os.Bundle
import android.view.Choreographer
import androidx.appcompat.app.AppCompatActivity
import android.preference.PreferenceManager
import com.cttr.hdlauncher.databinding.ActivityDemoBinding

class GamepadDemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDemoBinding
    private var lastFrame = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val dt = if (lastFrame == 0L) 0.016f else ((frameTimeNanos - lastFrame) / 1_000_000_000f)
            lastFrame = frameTimeNanos
            binding.playfield.update(dt.coerceIn(0f, 0.05f))
            binding.playfield.invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.gamepad.opacity = prefs.getFloat("gp_opacity", 0.55f)
        binding.gamepad.flipSides = prefs.getBoolean("gp_flip", false)
        binding.playfield.gamepadProvider = { binding.gamepad.getState() }

        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onPause() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onPause()
    }
}
