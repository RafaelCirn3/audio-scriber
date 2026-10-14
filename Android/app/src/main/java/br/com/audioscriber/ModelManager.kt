package br.com.audioscriber

import android.content.Context
import android.os.StatFs
import java.io.File
import java.util.zip.ZipInputStream

/**
 * The Portuguese Vosk model is downloaded and verified at BUILD time.
 * No network connection is required on the user's device.
 */
class ModelManager(private val context: Context) {
    companion object {
        private const val NAME = "vosk-model-small-pt-0.3"
        private const val MODEL_REVISION = "endpoint-silence-v2"
        private const val ASSET = "vosk-model-small-pt-0.3.zip"
        private const val MAX_UNPACKED = 350L * 1024 * 1024
    }

    private fun missing(dir: File): List<String> =
        listOf("am/final.mdl", "conf/mfcc.conf", "graph").filter {
            val path = File(dir, it)
            if (it == "graph") !path.isDirectory else !path.isFile
        }

    fun ensureModel(progress: (String) -> Unit): File {
        val folder = File(context.filesDir, "models")
        val model = File(folder, NAME)
        val marker = File(model, ".ready")
        if (marker.isFile && marker.readText().trim() == MODEL_REVISION && missing(model).isEmpty()) return model

        check(folder.exists() || folder.mkdirs()) { "Não foi possível criar pasta de modelos" }
        val free = StatFs(folder.absolutePath).availableBytes
        check(free >= 250L * 1024 * 1024) {
            "Espaço insuficiente para preparar o modelo offline (250 MB livres necessários)"
        }
        val temp = File(folder, "installing")
        temp.deleteRecursively()
        check(temp.mkdirs()) { "Não foi possível preparar instalação do modelo" }
        try {
            progress("Preparando modelo português incluído no aplicativo...")
            var unpacked = 0L
            var entries = 0
            context.assets.open(ASSET).buffered().use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val destination = File(temp, entry.name)
                        check(destination.canonicalPath.startsWith(temp.canonicalPath + File.separator)) {
                            "Caminho inválido dentro do pacote do modelo"
                        }
                        if (entry.isDirectory) {
                            check(destination.isDirectory || destination.mkdirs()) { "Falha ao criar diretório" }
                        } else {
                            check(destination.parentFile?.isDirectory == true || destination.parentFile?.mkdirs() == true) {
                                "Falha ao criar diretório do modelo"
                            }
                            destination.outputStream().buffered().use { output ->
                                val bytes = ByteArray(32768)
                                while (true) {
                                    val count = zip.read(bytes)
                                    if (count == -1) break
                                    unpacked += count
                                    check(unpacked <= MAX_UNPACKED) { "Modelo excede limite de extração" }
                                    output.write(bytes, 0, count)
                                }
                            }
                        }
                        entries++
                        zip.closeEntry()
                    }
                }
            }
            check(entries > 0) { "Modelo incorporado está vazio" }
            val candidate = temp.walkTopDown().maxDepth(4)
                .firstOrNull { it.isDirectory && missing(it).isEmpty() }
                ?: error("Modelo incorporado incompleto: arquivos Vosk essenciais não encontrados")
            progress("Finalizando instalação offline...")
            // Do not destroy an existing installation until the replacement is ready.
            val replacement = File(folder, "prepared")
            replacement.deleteRecursively()
            check(candidate.renameTo(replacement)) { "Falha ao preparar modelo" }
            model.deleteRecursively()
            check(replacement.renameTo(model)) { "Falha ao instalar modelo" }
            File(model, ".ready").writeText(MODEL_REVISION)
            return model
        } finally {
            temp.deleteRecursively()
        }
    }
}
