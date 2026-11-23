package com.cod3uchiha.aicamera

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the pinned Qualcomm ESRGAN TFLite model on first launch.
 * The model is stored privately and inference remains fully on-device.
 */
object ModelDownloader {
    private const val MODEL_URL =
        "https://huggingface.co/qualcomm/ESRGAN/resolve/4000626f7488977ec4273928c6b037f2fd3181e3/ESRGAN.tflite"
    private const val SHA256 =
        "d146a0051f9c1f7b183bbbfee391857a2fad2bdd37caff53d6c8c5c1f3bb7cc2"
    private const val EXPECTED_BYTES = 67_000_000L

    suspend fun ensureModel(
        context: Context,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "models").apply { mkdirs() }
        val model = File(directory, "esrgan.tflite")
        if (model.exists() && sha256(model) == SHA256) return@withContext model
        model.delete()
        val partial = File(directory, "esrgan.tflite.part")
        partial.delete()

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Cod3uchiha-AICamera/0.2")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Model server returned HTTP ${connection.responseCode}"
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_BYTES
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var downloaded = 0L
                    var lastUpdate = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (downloaded - lastUpdate >= 512 * 1024 || downloaded == total) {
                            lastUpdate = downloaded
                            withContext(Dispatchers.Main) { onProgress(downloaded, total) }
                        }
                    }
                }
            }
            check(partial.length() > 10_000_000L) { "Downloaded model is unexpectedly small" }
            check(sha256(partial) == SHA256) { "Model checksum verification failed" }
            check(partial.renameTo(model)) { "Could not install downloaded model" }
            model
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}