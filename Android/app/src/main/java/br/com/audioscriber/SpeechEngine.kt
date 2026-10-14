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

    fun readRecording(file: File): ShortArray {
        check(file.length() in 2..(16000L * 2 * 60 * 15)) { "Gravação vazia ou acima de 15 minutos" }
        val pcm = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(pcm.remaining() / 2) { pcm.short }
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
                                check(collected.size() + info.size <= 180 * 1024 * 1024) { "Arquivo muito grande" }
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
