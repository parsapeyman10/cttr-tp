package com.cttr.hdlauncher

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Copies the extracted Crash Tag Team Racing HD texture pack into an emulator's
 * textures folder using the Storage Access Framework (SAF). This avoids needing
 * dangerous MANAGE_EXTERNAL_STORAGE permission.
 *
 * Flow in the UI:
 *   source tree = the extracted texture pack root
 *   dest tree   = .../textures/<SERIAL>/replacements   (picked inside the emulator folder)
 */
object TextureInstaller {

    private data class Counters(var copied: Int = 0, var total: Int = 0)

    suspend fun copyTree(
        context: Context,
        sourceTree: Uri,
        destTree: Uri,
        onProgress: suspend (copied: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val src = DocumentFile.fromTreeUri(context, sourceTree)
        val dst = DocumentFile.fromTreeUri(context, destTree)
        if (src == null || dst == null) return@withContext -1

        val c = Counters()
        c.total = countFiles(src)
        copyDir(context, src, dst, c, onProgress)
        c.copied
    }

    private fun countFiles(dir: DocumentFile): Int {
        var n = 0
        for (f in dir.listFiles()) {
            n += if (f.isDirectory) countFiles(f) else if (f.isFile) 1 else 0
        }
        return n
    }

    private suspend fun copyDir(
        context: Context,
        src: DocumentFile,
        dst: DocumentFile,
        c: Counters,
        onProgress: suspend (Int, Int) -> Unit
    ) {
        for (f in src.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) {
                val child = dst.findFile(name) ?: dst.createDirectory(name)
                if (child != null) copyDir(context, f, child, c, onProgress)
            } else if (f.isFile) {
                val target = dst.findFile(name)?.takeIf { it.isFile }
                    ?: dst.createFile(f.type ?: "application/octet-stream", name)
                if (target != null) {
                    try {
                        context.contentResolver.openInputStream(f.uri)?.use { input: InputStream ->
                            context.contentResolver.openOutputStream(target.uri)?.use { out: OutputStream ->
                                input.copyTo(out)
                            }
                        }
                        c.copied++
                    } catch (e: Exception) {
                        Log.w("TextureInstaller", "copy failed: $name", e)
                    }
                    onProgress(c.copied, c.total)
                }
            }
        }
    }
}
