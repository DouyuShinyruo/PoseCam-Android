package com.posecam.app.wireframe

import android.graphics.Bitmap
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 线框模式 V6：Canny 风格「强度双阈值 + 有界迟滞链接」（2026-08 用真实小红书
 * 风景照片对比 V2/V4/V5 调参定型）。
 *
 * 之前版本的问题：
 * - V2 全边缘：纹理/噪点全画，糊成网
 * - V4 连通块分类：真实照片边缘碎成 2000-3000 段，长度门槛把主体线也杀了（断续虚线）
 * - V5 桥接：4% 密度边缘网桥接后连成一片，退回全保留
 *
 * V6 流程：
 *  1. 灰度 + 双重 5tap 高斯降噪（σ≈2.0：抹平草叶/树纹等高频纹理，保留大尺度强边界）
 *  2. Sobel 梯度幅值 + 方向
 *  3. 非极大值抑制 NMS（1px 细线）
 *  4. 强种子：全图梯度最强的 strongTarget 比例像素（山脊/地平线/主体轮廓）
 *  5. 迟滞链接：弱边缘（≥弱阈值比例×种子强度）仅在种子 5px 范围内保留——接续断线，
 *     孤立纹理网因无种子整片消失
 *  6. 连通域去噪（短碎杂物线剔除）
 *  7. 渲染：2px 加粗核心线 + 外环 + 白色光晕（缩放到取景器后仍清晰可见）
 *
 * @param detail 线稿精细度 0.25(简洁)/0.5(标准)/0.8(精细)，控制种子密度
 */
object EdgeDetector {

    fun toSketch(source: Bitmap, maxDim: Int = 1400, detail: Float = 0.5f): Bitmap {
        val src = scaleDown(source, maxDim)
        val w = src.width
        val h = src.height
        if (w < 16 || h < 16) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val n = w * h

        // 1. 灰度
        val pixels = IntArray(n)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = FloatArray(n)
        for (i in 0 until n) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // 2. 降噪（σ≈2.0：高频纹理被抹平，主体轮廓/车轮/山脊不受影响）
        val blur = blur5Sep(blur5Sep(gray, w, h), w, h)

        // 3. Sobel 幅值 + 量化方向（0/45/90/135）
        val mag = FloatArray(n)
        val dir = IntArray(n)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = -blur[i - w - 1] - 2f * blur[i - 1] - blur[i + w - 1] +
                    blur[i - w + 1] + 2f * blur[i + 1] + blur[i + w + 1]
                val gy = -blur[i - w - 1] - 2f * blur[i - w] - blur[i - w + 1] +
                    blur[i + w - 1] + 2f * blur[i + w] + blur[i + w + 1]
                mag[i] = sqrt(gx * gx + gy * gy)
                val deg = (Math.toDegrees(atan2(gy, gx).toDouble()) + 180.0) % 180.0
                dir[i] = (deg / 45.0).toInt()
            }
        }

        // 4. NMS：沿梯度方向与两侧邻居比较，只留局部极大 -> 细线
        val thin = FloatArray(n)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val m = mag[i]
                if (m <= 0f) continue
                val n1: Float
                val n2: Float
                when (dir[i]) {
                    0 -> { n1 = mag[i - 1]; n2 = mag[i + 1] }                 // 水平梯度：左右
                    1 -> { n1 = mag[i + w + 1]; n2 = mag[i - w - 1] }         // 45°：右下/左上
                    2 -> { n1 = mag[i - w]; n2 = mag[i + w] }                 // 垂直：上下
                    else -> { n1 = mag[i - w + 1]; n2 = mag[i + w - 1] }      // 135°：右上/左下
                }
                if (m >= n1 && m >= n2) thin[i] = m
            }
        }

        // 5. 参数档位（用户在真实照片对比 B/E/F 后选定 F 为标准档）
        val (strongTarget, weakRatio, minSize) = when {
            detail <= 0.3f -> Triple(0.006f, 0.55f, 20)  // 简洁：最干净
            detail >= 0.7f -> Triple(0.010f, 0.45f, 12)  // 精细：略多细节
            else -> Triple(0.008f, 0.50f, 16)            // 标准：F 参数
        }
        val strongK = max(1, (strongTarget * n).toInt())
        val strongThresh = kthLargest(thin, strongK)
        if (strongThresh <= 0f) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        // 6. 迟滞链接：弱边缘仅在种子 4px 范围内保留
        val strong = ByteArray(n)
        for (i in 0 until n) if (thin[i] >= strongThresh) strong[i] = 1
        val linked = dilateRadius(strong, w, h, radius = 5)
        val weakThresh = weakRatio * strongThresh
        val mask = ByteArray(n)
        for (i in 0 until n) {
            if (thin[i] >= weakThresh && linked[i].toInt() == 1) mask[i] = 1
        }

        // 7. 连通域去噪
        ccFilter(mask, w, h, minSize = minSize)

        // 8. 渲染
        return render(mask, w, h)
    }

    /** 可分离 5tap 高斯 [1,4,6,4,1]/16，等价 σ≈1.4 */
    private fun blur5Sep(src: FloatArray, w: Int, h: Int): FloatArray {
        val kernel = floatArrayOf(1f, 4f, 6f, 4f, 1f)
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var acc = 0f
                for (t in -2..2) {
                    val xx = (x + t).coerceIn(0, w - 1)
                    acc += src[row + xx] * kernel[t + 2]
                }
                tmp[row + x] = acc / 16f
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var acc = 0f
                for (t in -2..2) {
                    val yy = (y + t).coerceIn(0, h - 1)
                    acc += tmp[yy * w + x] * kernel[t + 2]
                }
                out[y * w + x] = acc / 16f
            }
        }
        return out
    }

    /** 直方图法求第 k 大的值（k 从 1 开始） */
    private fun kthLargest(values: FloatArray, k: Int): Float {
        var maxV = 0f
        for (v in values) if (v > maxV) maxV = v
        if (maxV <= 0f) return 0f
        val bins = 2048
        val hist = IntArray(bins)
        val scale = (bins - 1) / maxV
        for (v in values) {
            if (v > 0f) hist[(v * scale).toInt().coerceAtMost(bins - 1)]++
        }
        var count = 0
        for (b in bins - 1 downTo 0) {
            count += hist[b]
            if (count >= k) return b / scale
        }
        return 0f
    }

    /** 半径 r 的 8 邻域迭代膨胀 */
    private fun dilateRadius(src: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        var current = src.copyOf()
        repeat(radius) {
            val next = ByteArray(current.size)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    next[i] = if (current[i].toInt() == 1 || hasNeighbor(current, w, h, x, y)) 1 else 0
                }
            }
            current = next
        }
        return current
    }

    /** 8 邻域连通域过滤，删除面积小于 minSize 的噪点 */
    private fun ccFilter(mask: ByteArray, w: Int, h: Int, minSize: Int) {
        val n = w * h
        val visited = ByteArray(n)
        val queue = IntArray(n)
        val component = IntArray(n)
        for (start in 0 until n) {
            if (mask[start].toInt() == 0 || visited[start].toInt() == 1) continue
            var head = 0
            var tail = 0
            var size = 0
            visited[start] = 1
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                component[size++] = i
                val y = i / w
                val x = i - y * w
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                        val j = ny * w + nx
                        if (mask[j].toInt() == 1 && visited[j].toInt() == 0) {
                            visited[j] = 1
                            queue[tail++] = j
                        }
                    }
                }
            }
            if (size < minSize) {
                for (t in 0 until size) mask[component[t]] = 0
            }
        }
    }

    /** 2px 加粗核心（dilate×1）245 + 外环 190 + 白色光晕 70 */
    private fun render(mask: ByteArray, w: Int, h: Int): Bitmap {
        val n = w * h
        // core = mask 膨胀 1px（加粗）；ring = core 外一圈
        val core = ByteArray(n)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                core[i] = if (mask[i].toInt() == 1 || hasNeighbor(mask, w, h, x, y)) 1 else 0
            }
        }
        val ring = ByteArray(n)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (core[i].toInt() == 1) continue
                if (hasNeighbor(core, w, h, x, y)) ring[i] = 1
            }
        }
        val out = IntArray(n)
        val coreColor = (0x1C shl 16) or (0x1C shl 8) or 0x20
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                out[i] = when {
                    core[i].toInt() == 1 -> (245 shl 24) or coreColor
                    ring[i].toInt() == 1 -> (190 shl 24) or coreColor
                    hasNeighbor(ring, w, h, x, y) -> (70 shl 24) or (0xF4 shl 16) or (0xF6 shl 8) or 0xFA
                    else -> 0x00000000
                }
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun hasNeighbor(flags: ByteArray, w: Int, h: Int, x: Int, y: Int): Boolean {
        val left = x > 0
        val right = x < w - 1
        val up = y > 0
        val down = y < h - 1
        val i = y * w + x
        if (left && flags[i - 1].toInt() == 1) return true
        if (right && flags[i + 1].toInt() == 1) return true
        if (up && flags[i - w].toInt() == 1) return true
        if (down && flags[i + w].toInt() == 1) return true
        if (left && up && flags[i - w - 1].toInt() == 1) return true
        if (right && up && flags[i - w + 1].toInt() == 1) return true
        if (left && down && flags[i + w - 1].toInt() == 1) return true
        if (right && down && flags[i + w + 1].toInt() == 1) return true
        return false
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
