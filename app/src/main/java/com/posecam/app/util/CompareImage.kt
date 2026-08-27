package com.posecam.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/**
 * 生成「参考图 vs 成片」对比拼图：
 * 3:4 画布，左右两个 Fit 面板 + 标题/标签/分隔线，可直接保存或分享。
 */
object CompareImage {

    private const val BG = 0xFF101014.toInt()
    private const val ACCENT = 0xFFFFD166.toInt()
    private const val WHITE = 0xFFF2F2F4.toInt()
    private const val GRAY = 0xFFB9B9C3.toInt()

    fun create(reference: Bitmap, photo: Bitmap, outWidth: Int = 1440): Bitmap {
        val outH = outWidth * 4 / 3
        val out = Bitmap.createBitmap(outWidth, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(BG)

        val gap = 10f
        val topPad = 110f
        val labelH = 100f
        val bottomPad = 60f
        val panelW = (outWidth - gap * 3f) / 2f
        val panelH = outH - topPad - labelH - bottomPad

        // 左：参考图；右：成片
        drawFit(canvas, reference, RectF(gap, topPad, gap + panelW, topPad + panelH))
        drawFit(
            canvas, photo,
            RectF(gap * 2 + panelW, topPad, gap * 2 + panelW * 2, topPad + panelH)
        )

        // 中缝分隔线
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            strokeWidth = 6f
        }
        val cx = outWidth / 2f
        canvas.drawLine(cx, topPad - 30f, cx, topPad + panelH + 30f, linePaint)

        // 标题
        val title = textPaint(52f, WHITE, bold = true)
        val titleText = "照样对比"
        val tw = title.measureText(titleText)
        canvas.drawText(titleText, cx - tw / 2f, topPad - 42f, title)

        // 底部标签
        val label = textPaint(42f, GRAY, bold = false)
        canvas.drawText("参考图", gap, topPad + panelH + 66f, label)
        val rightLabel = "成片"
        canvas.drawText(
            rightLabel,
            outWidth - gap - label.measureText(rightLabel),
            topPad + panelH + 66f,
            textPaint(42f, ACCENT, bold = true)
        )

        return out
    }

    private fun drawFit(canvas: Canvas, source: Bitmap, rect: RectF) {
        if (source.width <= 0 || source.height <= 0) return
        val scale = min(rect.width() / source.width, rect.height() / source.height)
        val w = source.width * scale
        val h = source.height * scale
        val left = rect.left + (rect.width() - w) / 2f
        val top = rect.top + (rect.height() - h) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(source, null, RectF(left, top, left + w, top + h), paint)
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
}
