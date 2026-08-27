package com.posecam.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

object Images {

    fun decode(file: File, maxDim: Int = 2560): Bitmap? =
        decodeBytes({ opts -> BitmapFactory.decodeFile(file.absolutePath, opts) }, maxDim)

    fun decode(context: Context, uri: Uri, maxDim: Int = 2560): Bitmap? =
        decodeBytes({ opts -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } }, maxDim)

    fun decodeResource(context: Context, resId: Int, maxDim: Int = 1440): Bitmap? =
        decodeBytes({ opts -> BitmapFactory.decodeResource(context.resources, resId, opts) }, maxDim)

    /** 按 EXIF 方向旋转照片，保证合成时照片方向正确。 */
    fun applyExif(file: File, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** 保存到系统相册 Pictures/PoseCam，返回可分享的 content Uri。 */
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PoseCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            } ?: throw IllegalStateException("无法写入图片")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private inline fun decodeBytes(
        decoder: (BitmapFactory.Options?) -> Bitmap?,
        maxDim: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decoder(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return decoder(opts)
    }
}
