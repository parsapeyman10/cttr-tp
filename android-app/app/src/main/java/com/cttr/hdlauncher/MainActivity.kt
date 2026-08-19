package com.cttr.hdlauncher

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Switch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.preference.PreferenceManager
import com.cttr.hdlauncher.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var sourceTree: Uri? = null
    private var destTree: Uri? = null

    private val STEP_SOURCE = 1
    private val STEP_DEST = 2
    private var pendingStep = 0

    private val treePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@registerForActivityResult
        when (pendingStep) {
            STEP_SOURCE -> {
                sourceTree = uri
                pendingStep = STEP_DEST
                binding.status.text = "منبع انتخاب شد. حالا پوشهٔ مقصد (replacements) ایمولاتور را انتخاب کن."
                treePicker.launch(null)
            }
            STEP_DEST -> {
                destTree = uri
                runInstall()
            }
        }
    }

    private val isoPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        launchGame(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Use a bundled Crash Tag Team Racing texture as the background.
        try {
            val bmp = BitmapFactory.decodeStream(assets.open("cttr/menu/main_bg.png"))
            val sw = resources.displayMetrics.widthPixels
            val sh = resources.displayMetrics.heightPixels
            val scaled = Bitmap.createScaledBitmap(bmp, sw, sh, true)
            binding.root.background = BitmapDrawable(resources, scaled)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        binding.btnInstall.setOnClickListener {
            pendingStep = STEP_SOURCE
            treePicker.launch(null)
        }
        binding.btnLaunch.setOnClickListener { onLaunchClicked() }
        binding.btnDemo.setOnClickListener {
            startActivity(Intent(this, GamepadDemoActivity::class.java))
        }
        binding.btnSettings.setOnClickListener { showSettings() }
    }

    // ---- Texture installation ----
    private fun runInstall() {
        val src = sourceTree ?: return
        val dst = destTree ?: return
        binding.status.text = "در حال کپی تکسچرها…"
        lifecycleScope.launch {
            val copied = TextureInstaller.copyTree(this@MainActivity, src, dst) { c, t ->
                runOnUiThread { binding.status.text = "کپی شد $c / $t" }
            }
            runOnUiThread {
                binding.status.text = if (copied > 0) {
                    "✅ $copied فایل تکسچر کپی شد. حالا بازی را از داخل ایمولاتور اجرا کن."
                } else {
                    "❌ کپی انجام نشد. مسیرها را بررسی کن."
                }
            }
        }
    }

    // ---- Emulator launching ----
    private fun onLaunchClicked() {
        val installed = EmulatorLauncher.installed(packageManager)
        if (installed.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("ایمولاتور پیدا نشد")
                .setMessage("لطفاً NetherSX2 یا AetherSX2 را نصب کن. سپس فایل ISO بازی را از داخل خودِ ایمولاتور اجرا کن.")
                .setPositiveButton("مشاهده NetherSX2") { _, _ ->
                    openWeb("https://github.com/Trixarian/NetherSX2-patch")
                }
                .setNegativeButton("باشه", null)
                .show()
            return
        }
        isoPicker.launch(arrayOf("*/*"))
    }

    private fun launchGame(isoUri: Uri) {
        val installed = EmulatorLauncher.installed(packageManager)
        val go: (String) -> Unit = { pkg ->
            if (!EmulatorLauncher.bootIso(this, pkg, isoUri)) {
                EmulatorLauncher.openEmulator(this, pkg)
            }
        }
        if (installed.size == 1) {
            go(installed[0].pkg)
        } else {
            val names = installed.map { it.name }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("ایمولاتور را انتخاب کن")
                .setItems(names) { _, i -> go(installed[i].pkg) }
                .show()
        }
    }

    private fun openWeb(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    // ---- Gamepad settings ----
    private fun showSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val seek = view.findViewById<SeekBar>(R.id.seekOpacity)
        val sw = view.findViewById<Switch>(R.id.switchFlip)
        seek.progress = (prefs.getFloat("gp_opacity", 0.55f) * 100).toInt()
        sw.isChecked = prefs.getBoolean("gp_flip", false)

        AlertDialog.Builder(this)
            .setTitle("تنظیمات گیم‌پد")
            .setView(view)
            .setPositiveButton("ذخیره") { _, _ ->
                prefs.edit()
                    .putFloat("gp_opacity", seek.progress / 100f)
                    .putBoolean("gp_flip", sw.isChecked)
                    .apply()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
