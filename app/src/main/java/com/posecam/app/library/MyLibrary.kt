package com.posecam.app.library

import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

/** 用户的专属素材库：导入的参考图保存在应用私有目录。 */
object MyLibrary {

    private val extensions = setOf("jpg", "jpeg", "png", "webp")

    fun dir(context: Context): File =
        File(context.filesDir, "library").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension.lowercase() in extensions }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** 把相册选中的图片复制进素材库，返回新文件。 */
    fun import(context: Context, uri: Uri): File? {
        return try {
            val target = File(dir(context), "ref_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (target.length() > 0) target else null.also { target.delete() }
        } catch (e: Exception) {
            null
        }
    }

    fun delete(file: File) {
        file.delete()
    }

    /** 从网络 URL 下载图片进素材库（小红书 CDN 图，带 UA/Referer 降低 403 概率）。 */
    fun importFromUrl(
        context: Context,
        url: String,
        userAgent: String? = null,
        referer: String? = null
    ): File? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            userAgent?.let { connection.setRequestProperty("User-Agent", it) }
            referer?.let { connection.setRequestProperty("Referer", it) }
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return null
            }
            val contentType = connection.contentType ?: ""
            val ext = when {
                contentType.contains("png", ignoreCase = true) -> "png"
                contentType.contains("webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }
            val target = File(
                dir(context),
                "ref_xhs_${System.currentTimeMillis()}_${(100..999).random()}.$ext"
            )
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()
            if (target.length() > 0) target else null.also { target.delete() }
        } catch (e: Exception) {
            null
        }
    }
}
