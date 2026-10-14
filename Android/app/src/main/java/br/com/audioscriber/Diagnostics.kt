package br.com.audioscriber

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persistent breadcrumbs survive a native Vosk abort or low-memory process termination. */
class Diagnostics(private val context: Context) {
    private val file get() = File(context.filesDir, "audio_scriber_diagnostics.log")
    @Synchronized fun record(stage: String, error: Throwable? = null) {
        val detail = error?.let { "\n" + Log.getStackTraceString(it) } ?: ""
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$timestamp pid=${Process.myPid()} stage=$stage$detail\n"
        Log.e("AudioScriber", stage, error)
        try {
            val log = file
            if (log.exists() && log.length() > 128 * 1024) {
                val previous = log.readText()
                log.writeText(previous.takeLast(64 * 1024))
            }
            log.appendText(line)
        } catch (ignored: Exception) {
            Log.e("AudioScriber", "Failed to persist diagnostics", ignored)
        }
    }
    fun report(): String = buildString {
        appendLine("Audio Scriber diagnostic report")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Available heap: ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
        appendLine()
        append(file.takeIf { it.exists() }?.readText()?.takeLast(48 * 1024) ?: "No diagnostics yet")
    }
}
