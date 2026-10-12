package dev.hailab.deeppilot.attachments

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Scratch files the system camera writes photos into (shared through our FileProvider). */
object CameraFiles {
    fun newPhoto(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        // Only the latest few shots are ever needed; older ones were already imported.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
