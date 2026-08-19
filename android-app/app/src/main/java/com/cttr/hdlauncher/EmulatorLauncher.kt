package com.cttr.hdlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class Emu(val name: String, val pkg: String)

/**
 * Helpers to detect and launch a PS2 emulator (NetherSX2 / AetherSX2) on Android.
 * Booting an ISO via ACTION_VIEW works in recent builds; if it fails we fall
 * back to just opening the emulator.
 */
object EmulatorLauncher {

    val KNOWN = listOf(
        Emu("NetherSX2", "com.nether.netherSX2"),
        Emu("NetherSX2 (alpha)", "com.nether.netherSX2.alpha"),
        Emu("AetherSX2", "com.aethersx2.android")
    )

    fun installed(pm: PackageManager): List<Emu> =
        KNOWN.filter {
            try {
                pm.getPackageInfo(it.pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }

    fun openEmulator(context: Context, pkg: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun bootIso(context: Context, pkg: String, isoUri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, isoUri).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "application/octet-stream"
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openEmulator(context, pkg)
        }
    }
}
