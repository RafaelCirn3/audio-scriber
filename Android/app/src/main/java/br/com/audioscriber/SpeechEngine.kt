package br.com.audioscriber

import android.content.Context
import android.media.*
import android.net.Uri
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeechEngine(private val context: Context) {
    private val rate = 16000
    fun transcribe(modelDir: File, samples: ShortArray): String {
        val parts = mutableListOf<String>()
        Model(modelDir.absolutePath).use { model ->
            Recognizer(model, rate.toFloat()).use { recognizer ->
                var start = 0
                while (start < samples.size) {
                    val end = minOf(start + 4096, samples.size)
                    val bytes = ByteBuffer.allocate((end - start) * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in start until end) bytes.putShort(samples[i])
                    if (recognizer.acceptWaveForm(bytes.array(), bytes.position())) {
                        val result = JSONObject(recognizer.result).optString("text").trim()
                        if (result.isNotEmpty()) parts.add(result)
                    }
                    start = end
                }
                val last = JSONObject(recognizer.finalResult).optString("text").trim()
                if (last.isNotEmpty()) parts.add(last)
            }
        }
        return parts.joinToString(" ")
    }

    /**
     * Stream raw 16 kHz mono PCM to Vosk. Avoids allocating the full recording
     * as ByteArray + ShortArray (important on low-memory Android devices).
     */
    fun transcribeRecording(modelDir: File, file: File, progress: (String) -> Unit): String {
        check(file.length() in 2..(16000L * 2 * 60 * 15)) {
            "Gravação vazia ou acima de 15 minutos"
        }
        val parts = mutableListOf<String>()
        val diagnostics = Diagnostics(context)
        diagnostics.record("Recording bytes=${file.length()} durationSec=${file.length() / 32000.0}")
        progress("Carregando modelo de português...")
        Model(modelDir.absolutePath).use { model ->
            progress("Inicializando reconhecimento...")
            Recognizer(model, rate.toFloat()).use { recognizer ->
                progress("Transcrevendo gravação...")
                file.inputStream().buffered().use { input ->
                    val bytes = ByteArray(8192)
                    var chunks = 0
                    var peak = 0
                    var active = 0L
                    var totalSamples = 0L
                    while (true) {
                        val n = input.read(bytes)
                        if (n < 0) break
                        val usable = n - (n % 2)
                        if (usable > 0) {
                            var i = 0
                            while (i + 1 < usable) {
                                val value = (bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)
                                val sample = value.toShort().toInt()
                                peak = maxOf(peak, kotlin.math.abs(sample))
                                if (kotlin.math.abs(sample) > 400) active++
                                totalSamples++
                                i += 2
                            }
                            chunks++
                            if (chunks == 1 || chunks % 20 == 0) diagnostics.record("Before Vosk chunk=$chunks bytes=$usable peak=$peak activeSamples=$active/$totalSamples nativeHeapMB=${android.os.Debug.getNativeHeapAllocatedSize() / 1048576}")
                            val completed = recognizer.acceptWaveForm(bytes, usable)
                            if (chunks == 1 || chunks % 20 == 0) diagnostics.record("After Vosk chunk=$chunks")
                            if (completed) {
                            val text = JSONObject(recognizer.result).optString("text").trim()
                            if (text.isNotEmpty()) parts.add(text)
                            }
                        }
                    }
                }
                diagnostics.record("Before Vosk finalResult chunks=$chunks peak=$peak activeSamples=$active/$totalSamples")
                val text = JSONObject(recognizer.finalResult).optString("text").trim()
                if (text.isNotEmpty()) parts.add(text)
                diagnostics.record("After Vosk finalResult chars=${parts.joinToString(" ").length}")
                if (parts.isEmpty()) diagnostics.record("No speech recognized; peak=$peak activeSamples=$active/$totalSamples")
            }
        }
        return parts.joinToString(" ")
    }

    fun readRecording(file: File): ShortArray {
        check(file.length() in 2..(16000L * 2 * 60 * 15)) { "Gravação vazia ou acima de 15 minutos" }
        val pcm = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(pcm.remaining() / 2) { pcm.short }
    }


    /**
     * Decode an imported audio file directly to 16 kHz mono PCM on disk.
     * Memory stays bounded by the codec's output buffer (no full-file arrays).
     * Nearest-neighbor resampling prioritizes low memory over fidelity.
     */
    fun decodeImportedToPcm(uri: Uri, destination: File, progress: (String) -> Unit) {
        val diagnostics = Diagnostics(context)
        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r").use { descriptor ->
                requireNotNull(descriptor) { "Arquivo indisponível" }
                extractor.setDataSource(descriptor.fileDescriptor)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Arquivo sem faixa de áudio")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var inputDone = false
                var outputDone = false
                var sourceFrames = 0L
                var outputFrames = 0L
                var idle = 0
                progress("Decodificando em blocos para armazenamento temporário...")
                destination.outputStream().buffered().use { output ->
                    val out = ByteArray(8192)
                    var outPos = 0
                    while (!outputDone) {
                        if (!inputDone) {
                            val index = codec.dequeueInputBuffer(10000)
                            if (index >= 0) {
                                val buffer = codec.getInputBuffer(index)!!
                                buffer.clear()
                                val size = extractor.readSampleData(buffer, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        when (val index = codec.dequeueOutputBuffer(info, 10000)) {
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val decoded = codec.outputFormat
                                sourceRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                val encoding = if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                    decoded.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                else AudioFormat.ENCODING_PCM_16BIT
                                check(encoding == AudioFormat.ENCODING_PCM_16BIT) { "Codec não retornou PCM de 16 bits" }
                                check(channels in 1..2 && sourceRate in 8000..192000) { "Formato de áudio não suportado" }
                                diagnostics.record("Decoder format rate=$sourceRate channels=$channels")
                            }
                            MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                idle++
                                check(idle < 500) { "Tempo limite de decodificação" }
                            }
                            else -> if (index >= 0) {
                                idle = 0
                                if (info.size > 0) {
                                    check(channels in 1..2 && sourceRate in 8000..192000)
                                    val buffer = codec.getOutputBuffer(index)!!
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    val frameBytes = 2 * channels
                                    while (buffer.remaining() >= frameBytes) {
                                        var sum = 0
                                        repeat(channels) {
                                            val lo = buffer.get().toInt() and 255
                                            val hi = buffer.get().toInt()
                                            sum += ((hi shl 8) or lo).toShort().toInt()
                                        }
                                        val sample = (sum / channels).toShort().toInt()
                                        sourceFrames++
                                        // Absolute frame counts prevent drift at codec buffer boundaries.
                                        val desired = sourceFrames * 16000L / sourceRate
                                        while (outputFrames < desired) {
                                            out[outPos++] = sample.toByte()
                                            out[outPos++] = (sample shr 8).toByte()
                                            if (outPos == out.size) {
                                                output.write(out, 0, outPos)
                                                outPos = 0
                                            }
                                            outputFrames++
                                            check(outputFrames <= 16000L * 60 * 15) {
                                                "Duração máxima de 15 minutos excedida"
                                            }
                                        }
                                    }
                                    if (sourceFrames % (sourceRate.toLong() * 10) < 4096) {
                                        diagnostics.record("Decoded frames=$sourceFrames pcmFrames=$outputFrames nativeHeapMB=${android.os.Debug.getNativeHeapAllocatedSize() / 1048576}")
                                    }
                                }
                                outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(index, false)
                            }
                        }
                    }
                    if (outPos > 0) output.write(out, 0, outPos)
                }
                check(outputFrames > 0) { "Áudio importado vazio" }
                diagnostics.record("Imported PCM complete frames=$outputFrames")
            } finally {
                try { codec.stop() } finally { codec.release() }
            }
        } catch (failure: Throwable) {
            destination.delete()
            diagnostics.record("Imported audio decode failed", failure)
            throw failure
        } finally {
            extractor.release()
        }
    }

    fun readImported(uri: Uri): ShortArray {
        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
                requireNotNull(pfd) { "Arquivo indisponível" }
                extractor.setDataSource(pfd.fileDescriptor)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Nenhuma faixa de áudio")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                val collected = ByteArrayOutputStream()
                val maxDecodedBytes = minOf(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 6)
                var inputDone = false
                var outputDone = false
                var outputFormat = format
                var idle = 0
                while (!outputDone) {
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(10000)
                        if (index >= 0) {
                            val buf = codec.getInputBuffer(index)!!
                            val length = extractor.readSampleData(buf, 0)
                            if (length < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, length, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val index = codec.dequeueOutputBuffer(info, 10000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            idle++
                            check(idle < 500) { "Tempo limite de decodificação" }
                        }
                        else -> if (index >= 0) {
                            idle = 0
                            if (info.size > 0) {
                                check(collected.size().toLong() + info.size <= maxDecodedBytes) {
                                    "Áudio excede o limite de memória desta versão. Divida o arquivo em partes menores."
                                }
                                val buffer = codec.getOutputBuffer(index)!!
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                buffer.get(bytes)
                                collected.write(bytes)
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
                val encoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                    outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                check(encoding == AudioFormat.ENCODING_PCM_16BIT) { "O codec não retornou PCM de 16 bits" }
                val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val sourceRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                check(channels in 1..2 && sourceRate > 0) { "Áudio não suportado" }
                val pcm = ByteBuffer.wrap(collected.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
                val frames = pcm.remaining() / (2 * channels)
                check(frames > 0) { "Áudio vazio" }
                val mono = ShortArray(frames) {
                    var sum = 0
                    repeat(channels) { sum += pcm.short.toInt() }
                    (sum / channels).toShort()
                }
                val length = (frames.toDouble() * rate / sourceRate).toInt()
                check(length in 1..(rate * 60 * 15)) { "Limite de duração: 15 minutos" }
                return ShortArray(length) { i ->
                    val x = i.toDouble() * sourceRate / rate
                    val a = x.toInt().coerceIn(0, frames - 1)
                    val b = (a + 1).coerceAtMost(frames - 1)
                    val w = x - a
                    (mono[a] * (1 - w) + mono[b] * w).toInt().toShort()
                }
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }
}
