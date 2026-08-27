package com.posecam.app.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/**
 * 把参考图按取景器里的实时状态合成到拍摄照片上。
 *
 * 坐标映射：取景器容器与照片同为 4:3，PreviewView 使用 FILL_CENTER 时
 * 两者等比铺满，因此可以把叠加层的平移/缩放/旋转按线性比例直接换算。
 */
object Composite {

    fun draw(
        photo: Bitmap,
        reference: Bitmap,
        overlay: OverlayState,
        previewWidthPx: Float,
        previewHeightPx: Float,
        mirror: Boolean = false
    ): Bitmap {
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        if (!overlay.visible || overlay.alpha <= 0.02f) return out
        if (previewWidthPx <= 0f || previewHeightPx <= 0f) return out

        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val canvas = Canvas(out)

        val fx = w / previewWidthPx
        val fy = h / previewHeightPx

        // 参考图在取景器内 contentScale = Fit 时的基准尺寸（居中）
        val fit = min(previewWidthPx / reference.width, previewHeightPx / reference.height)
        val baseW = reference.width * fit
        val baseH = reference.height * fit
        val drawW = baseW * fx
        val drawH = baseH * fy

        // 前置摄像头预览是镜像的，照片不镜像：横向位移与旋转需要取反
        val dir = if (mirror) -1f else 1f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.alpha = (overlay.alpha * 255f).toInt()

        canvas.save()
        canvas.translate(w / 2f + overlay.offsetX * fx * dir, h / 2f + overlay.offsetY * fy)
        canvas.rotate(overlay.rotation * dir)
        canvas.scale(overlay.scale, overlay.scale)
        canvas.drawBitmap(
            reference,
            null,
            RectF(-drawW / 2f, -drawH / 2f, drawW / 2f, drawH / 2f),
            paint
        )
        canvas.restore()
        return out
    }
}
