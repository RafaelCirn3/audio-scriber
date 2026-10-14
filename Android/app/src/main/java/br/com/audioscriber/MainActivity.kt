package br.com.audioscriber

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.content.res.Configuration
import android.view.WindowManager
import android.view.Gravity
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val recording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var recordingFile: File? = null
    private var importedUri: android.net.Uri? = null
    private var busy = false
    private lateinit var recordButton: Button
    private lateinit var importButton: Button
    private lateinit var transcribeButton: Button
    private lateinit var saveButton: Button
    private lateinit var status: TextView
    private lateinit var result: EditText
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun button(label: String, onTap: () -> Unit): Button =
        Button(this).apply { text = label; textSize = 17f; setOnClickListener { onTap() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(242, 247, 255)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        scroll.addView(column)
        column.addView(TextView(this).apply {
            text = "Audio Scriber"; textSize = 28f; setTextColor(Color.rgb(20, 79, 185))
        })
        column.addView(TextView(this).apply {
            text = "Transcrição local em português • Android"; textSize = 15f
            setPadding(0, dp(6), 0, dp(22))
        })
        recordButton = button("● Grave um áudio") { toggleRecording() }
        importButton = button("↑ Envie um áudio") { importAudio() }
        column.addView(recordButton)
        column.addView(importButton)
        status = TextView(this).apply {
            text = "Primeira utilização: download do modelo. Depois funciona offline."
            textSize = 15f
            setPadding(0, dp(18), 0, dp(18))
        }
        column.addView(status)
        transcribeButton = button("Transcrever áudio") { transcribeAudio() }.apply { isEnabled = false }
        saveButton = button("Salvar .txt") { saveText() }.apply { isEnabled = false }
        column.addView(transcribeButton)
        column.addView(saveButton)
        column.addView(TextView(this).apply {
            text = "Transcrição"; textSize = 19f; setPadding(0, dp(20), 0, dp(6))
        })
        result = EditText(this).apply {
            hint = "O texto aparecerá aqui..."
            gravity = Gravity.TOP
            minLines = 8
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        column.addView(result, LinearLayout.LayoutParams(-1, dp(280)))
        setContentView(scroll)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Mantém a Activity e o áudio ativos ao girar o dispositivo.
    }
    private fun message(text: String) = runOnUiThread { status.text = text }
    private fun refresh() {
        recordButton.isEnabled = !busy
        importButton.isEnabled = !busy && !recording.get()
        transcribeButton.isEnabled = !busy && !recording.get() && recorder == null &&
            (recordingFile != null || importedUri != null)
        saveButton.isEnabled = !busy && result.text.isNotBlank()
    }

    private fun importAudio() {
        if (busy || recording.get()) return
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/mp4", "video/quicktime"))
        }, 100)
    }
    @Deprecated("Compatibility with Android 8+")
    override fun onActivityResult(code: Int, outcome: Int, data: Intent?) {
        super.onActivityResult(code, outcome, data)
        if (code == 100 && outcome == RESULT_OK) {
            importedUri = data?.data
            recordingFile = null
            result.setText("")
            message("Arquivo selecionado. Toque em Transcrever áudio.")
            refresh()
        }
        if (code == 101 && outcome == RESULT_OK && data?.data != null) {
            try {
                contentResolver.openOutputStream(data.data!!)?.use {
                    it.write(result.text.toString().toByteArray(Charsets.UTF_8))
                }
                message("Arquivo .txt salvo no Android.")
            } catch (exc: Exception) { message("Falha ao salvar: " + exc.message) }
        }
    }

    private fun toggleRecording() {
        if (busy) return
        if (recording.get()) {
            recording.set(false)
            recordButton.isEnabled = false
            message("Finalizando gravação...")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 200)
        } else startRecording()
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 200) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRecording()
            else message("Permissão do microfone negada.")
        }
    }
    private fun startRecording() {
        val minBuffer = AudioRecord.getMinBufferSize(
            16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) { message("Microfone indisponível."); return }
        try {
            val audio = AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, 8192))
            check(audio.state == AudioRecord.STATE_INITIALIZED)
            audio.startRecording()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            recorder = audio
            recording.set(true)
            importedUri = null
            val file = File(cacheDir, "recording.pcm")
            recordingFile = file
            result.setText("")
            recordButton.text = "■ Parar gravação"
            refresh()
            message("Gravando... toque em Parar gravação.")
            executor.execute {
                try {
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(4096)
                        var total = 0L
                        while (recording.get()) {
                            val size = audio.read(buffer, 0, buffer.size)
                            if (size > 0) {
                                output.write(buffer, 0, size)
                                total += size
                                if (total >= 16000L * 2 * 60 * 15) recording.set(false)
                            } else if (size < 0) error("Falha na captura")
                        }
                    }
                } catch (exc: Exception) {
                    message("Erro de gravação: " + exc.message)
                } finally {
                    try { audio.stop() } catch (_: Exception) {}
                    audio.release()
                    recorder = null
                    recording.set(false)
                    runOnUiThread {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        recordButton.text = "● Grave um áudio"
                        if (file.length() == 0L) recordingFile = null
                        message(if (file.length() > 0L) "Gravação encerrada." else "Gravação vazia; tente novamente.")
                        refresh()
                    }
                }
            }
        } catch (exc: Exception) {
            recorder?.release()
            recorder = null
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            message("Microfone indisponível: " + exc.message)
            refresh()
        }
    }
    private fun transcribeAudio() {
        if (busy || recording.get() || recorder != null) return
        busy = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        refresh()
        message("Preparando transcrição...")
        val sourceFile = recordingFile
        val sourceUri = importedUri
        executor.execute {
            try {
                val model = ModelManager(this).ensureModel(::message)
                message("Transcrevendo no tablet/celular...")
                val engine = SpeechEngine(this)
                val samples = if (sourceFile != null) engine.readRecording(sourceFile)
                    else engine.readImported(sourceUri ?: error("Áudio não selecionado"))
                val text = engine.transcribe(model, samples)
                runOnUiThread {
                    result.setText(text)
                    message(if (text.isBlank()) "Nenhuma fala identificada." else "Transcrição concluída.")
                    busy = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    refresh()
                }
            } catch (exc: Exception) {
                runOnUiThread {
                    message("Erro: " + exc.message)
                    busy = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    refresh()
                }
            }
        }
    }
    private fun saveText() {
        if (result.text.isBlank()) return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "transcricao.txt")
        }, 101)
    }
    override fun onDestroy() {
        recording.set(false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        executor.shutdown()
        super.onDestroy()
    }
}
