package br.com.audioscriber

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Primeiro uso precisa de internet. Modelo e inferência permanecem 100% locais depois. */
class ModelManager(private val context: Context) {
    companion object {
        private const val NAME = "vosk-model-small-pt-0.3"
        private const val URL_MODEL = "https://alphacephei.com/vosk/models/$NAME.zip"
        private const val SHA256 = "6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7"
        private const val MAX_UNPACKED = 350L * 1024 * 1024
    }

    fun ensureModel(progress: (String) -> Unit): File {
        val folder = File(context.filesDir, "models")
        val model = File(folder, NAME)
        val marker = File(model, ".ready")
        if (marker.exists()) return model
        folder.mkdirs()
        val availableBytes = android.os.StatFs(folder.absolutePath).availableBytes
        check(availableBytes >= 160L * 1024 * 1024) { "Espaço insuficiente. Libere pelo menos 160 MB para o modelo offline." }
        val zipFile = File(context.cacheDir, "$NAME.zip")
        val tempDir = File(folder, "tmp")
        tempDir.deleteRecursively()
        tempDir.mkdirs()
        try {
            progress("Baixando modelo português (primeiro uso)...")
            val connection = URL(URL_MODEL).openConnection()
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            val digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(connection.getInputStream()).use { input ->
                zipFile.outputStream().buffered().use { output ->
                    val bytes = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(bytes)
                        if (count == -1) break
                        total += count
                        if (total > 80L * 1024 * 1024) error("Download excedeu limite seguro")
                        digest.update(bytes, 0, count)
                        output.write(bytes, 0, count)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual == SHA256) { "Checksum do modelo inválido" }
            progress("Preparando modelo offline...")
            var totalExpanded = 0L
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(tempDir, entry.name)
                    check(target.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) { "Arquivo ZIP inválido" }
                    if (entry.isDirectory) target.mkdirs()
                    else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n == -1) break
                                totalExpanded += n
                                check(totalExpanded <= MAX_UNPACKED) { "Modelo descompactado excedeu limite" }
                                out.write(buffer, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val extracted = File(tempDir, NAME)
            check(extracted.isDirectory && File(extracted, "am").isDirectory) { "Pacote do modelo inválido" }
            model.deleteRecursively()
            check(extracted.renameTo(model)) { "Falha ao instalar modelo" }
            check(marker.createNewFile()) { "Falha ao finalizar instalação" }
            return model
        } finally {
            zipFile.delete()
            tempDir.deleteRecursively()
        }
    }
}
