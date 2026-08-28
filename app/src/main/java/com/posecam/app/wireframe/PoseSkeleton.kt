package com.posecam.app.wireframe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** 一次检测产出的两种人像参考图 */
data class PoseArt(
    val skeleton: Bitmap?,  // 纯火柴人
    val contour: Bitmap?    // 人物轮廓 + 火柴人（方案A）
)

/**
 * 骨架/轮廓模式：MediaPipe Pose Landmarker（端侧离线）。
 * - skeleton：33 关键点 -> 火柴人
 * - contour：人像分割掩码 -> 轮廓线，叠加火柴人（摆姿势的理想参考形态）
 * 未检测到人物时两者均为 null。
 */
object PoseSkeleton {

    private const val MODEL = "pose_landmarker_full.task"

    @Volatile
    private var landmarker: PoseLandmarker? = null

    private fun detector(context: Context): PoseLandmarker =
        landmarker ?: synchronized(this) {
            landmarker ?: PoseLandmarker.createFromOptions(
                context.applicationContext,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder().setModelAssetPath(MODEL).build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setOutputSegmentationMasks(true)
                    .build()
            ).also { landmarker = it }
        }

    fun detect(context: Context, source: Bitmap): PoseArt {
        val bitmap = scaleDown(source, 1400)
        return try {
            val result = detector(context).detect(BitmapImageBuilder(bitmap).build())
            val landmarks = result.landmarks().firstOrNull()
            if (landmarks.isNullOrEmpty()) return PoseArt(null, null)

            val skeleton = renderSkeleton(bitmap.width, bitmap.height, landmarks)

            // 读取人像分割掩码（VEC32F1 浮点，置信度 0..1）
            val maskImage = result.segmentationMasks().orElse(null)?.firstOrNull()
            val contour = if (maskImage != null) {
                try {
                    val mw = maskImage.width
                    val mh = maskImage.height
                    val floats = FloatArray(mw * mh)
                    ByteBufferExtractor.extract(maskImage)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .get(floats)
                    renderContour(
                        bitmap.width, bitmap.height, landmarks,
                        floats, mw, mh
                    )
                } catch (e: Exception) {
                    null
                } finally {
                    maskImage.close()
                }
            } else {
                null
            }
            PoseArt(skeleton, contour)
        } catch (e: Exception) {
            PoseArt(null, null)
        }
    }

    // ---- MediaPipe Pose 33 关键点索引 ----
    private const val NOSE = 0
    private const val LEFT_EAR = 7
    private const val RIGHT_EAR = 8
    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_ELBOW = 13
    private const val RIGHT_ELBOW = 14
    private const val LEFT_WRIST = 15
    private const val RIGHT_WRIST = 16
    private const val LEFT_INDEX = 19
    private const val RIGHT_INDEX = 20
    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24
    private const val LEFT_KNEE = 25
    private const val RIGHT_KNEE = 26
    private const val LEFT_ANKLE = 27
    private const val RIGHT_ANKLE = 28
    private const val LEFT_FOOT = 31
    private const val RIGHT_FOOT = 32

    private val BONES = listOf(
        LEFT_SHOULDER to RIGHT_SHOULDER,
        LEFT_SHOULDER to LEFT_HIP,
        RIGHT_SHOULDER to RIGHT_HIP,
        LEFT_HIP to RIGHT_HIP,
        LEFT_SHOULDER to LEFT_ELBOW,
        LEFT_ELBOW to LEFT_WRIST,
        RIGHT_SHOULDER to RIGHT_ELBOW,
        RIGHT_ELBOW to RIGHT_WRIST,
        LEFT_WRIST to LEFT_INDEX,
        RIGHT_WRIST to RIGHT_INDEX,
        LEFT_HIP to LEFT_KNEE,
        LEFT_KNEE to LEFT_ANKLE,
        RIGHT_HIP to RIGHT_KNEE,
        RIGHT_KNEE to RIGHT_ANKLE,
        LEFT_ANKLE to LEFT_FOOT,
        RIGHT_ANKLE to RIGHT_FOOT
    )

    private val JOINTS = listOf(
        LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW,
        LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
    )

    private fun renderSkeleton(
        width: Int,
        height: Int,
        landmarks: List<NormalizedLandmark>
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawSkeleton(Canvas(out), width, landmarks)
        return out
    }

    /** 在给定画布上绘制火柴人（深色主线 + 白色光晕） */
    private fun drawSkeleton(
        canvas: Canvas,
        width: Int,
        landmarks: List<NormalizedLandmark>
    ) {
        val height = canvas.height
        val stroke = max(6f, width * 0.005f)
        val coreColor = 0xF51C1C20.toInt()
        val haloColor = 0x46F4F6FA

        fun x(i: Int) = landmarks[i].x() * width
        fun y(i: Int) = landmarks[i].y() * height
        fun visible(i: Int) = landmarks.getOrNull(i)?.visibility()?.orElse(0f) ?: 0f

        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = haloColor
            strokeWidth = stroke * 2.2f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coreColor
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }

        for ((a, b) in BONES) {
            if (landmarks.size <= max(a, b)) continue
            if (visible(a) < 0.4f || visible(b) < 0.4f) continue
            canvas.drawLine(x(a), y(a), x(b), y(b), halo)
            canvas.drawLine(x(a), y(a), x(b), y(b), core)
        }

        if (landmarks.size > RIGHT_SHOULDER) {
            val headX: Float
            val headY: Float
            if (visible(LEFT_EAR) > 0.4f && visible(RIGHT_EAR) > 0.4f) {
                headX = (x(LEFT_EAR) + x(RIGHT_EAR)) / 2f
                headY = (y(LEFT_EAR) + y(RIGHT_EAR)) / 2f
            } else {
                headX = x(NOSE)
                headY = y(NOSE)
            }
            val shoulderW = if (visible(LEFT_SHOULDER) > 0.4f && visible(RIGHT_SHOULDER) > 0.4f) {
                hypot(x(LEFT_SHOULDER) - x(RIGHT_SHOULDER), y(LEFT_SHOULDER) - y(RIGHT_SHOULDER))
            } else {
                height * 0.12f
            }
            val r = (shoulderW * 0.34f).coerceAtLeast(stroke * 2.5f)
            canvas.drawCircle(headX, headY, r, halo)
            canvas.drawCircle(headX, headY, r, core)
        }

        val joint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = coreColor }
        for (i in JOINTS) {
            if (landmarks.size <= i || visible(i) < 0.5f) continue
            canvas.drawCircle(x(i), y(i), stroke * 0.85f, joint)
        }
    }

    /**
     * 人物轮廓 + 火柴人：
     * 掩码(低分辨率) 阈值二值化 -> 闭运算补洞 -> 放大到原图尺寸 ->
     * 边界带(mask 减腐蚀) 描线 + 白色光晕 -> 顶层画火柴人
     */
    private fun renderContour(
        width: Int,
        height: Int,
        landmarks: List<NormalizedLandmark>,
        maskFloats: FloatArray,
        maskW: Int,
        maskH: Int
    ): Bitmap? {
        // 1. 二值化
        var mask = ByteArray(maskW * maskH)
        for (i in maskFloats.indices) {
            if (maskFloats[i] > 0.5f) mask[i] = 1
        }
        if (!mask.any { it.toInt() == 1 }) return null

        // 2. 闭运算：膨胀再腐蚀，填补掩码内部小洞
        mask = erode(dilate(mask, maskW, maskH, 2), maskW, maskH, 2)

        // 3. 最近邻放大到原图尺寸
        val person = ByteArray(width * height)
        val sx = maskW.toFloat() / width
        val sy = maskH.toFloat() / height
        for (y in 0 until height) {
            val my = (y * sy).toInt().coerceAtMost(maskH - 1)
            val rowOff = my * maskW
            for (x in 0 until width) {
                val mx = (x * sx).toInt().coerceAtMost(maskW - 1)
                if (mask[rowOff + mx].toInt() == 1) person[y * width + x] = 1
            }
        }

        // 4. 轮廓带：person 减去向内腐蚀 ~3px
        val inner = erode(person, width, height, 3)
        val coreColor = (0x1C shl 16) or (0x1C shl 8) or 0x20
        val out = IntArray(width * height)
        for (i in out.indices) {
            if (person[i].toInt() == 1 && inner[i].toInt() == 0) {
                out[i] = (245 shl 24) or coreColor
            }
        }
        // 白色光晕：轮廓外扩 2px
        val haloBand = dilate(person, width, height, 2)
        for (i in out.indices) {
            if (haloBand[i].toInt() == 1 && person[i].toInt() == 0) {
                out[i] = (70 shl 24) or (0xF4 shl 16) or (0xF6 shl 8) or 0xFA
            }
        }

        val bitmap = Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawSkeleton(canvas, width, landmarks)
        return bitmap
    }

    // ---- 形态学辅助 ----

    private fun dilate(src: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
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

    private fun erode(src: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        var current = src.copyOf()
        repeat(radius) {
            val next = ByteArray(current.size)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    next[i] = if (hasAllNeighbors(current, w, h, x, y)) 1 else 0
                }
            }
            current = next
        }
        return current
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

    private fun hasAllNeighbors(flags: ByteArray, w: Int, h: Int, x: Int, y: Int): Boolean {
        val left = x > 0
        val right = x < w - 1
        val up = y > 0
        val down = y < h - 1
        if (!left || !right || !up || !down) return false
        val i = y * w + x
        return flags[i - 1].toInt() == 1 && flags[i + 1].toInt() == 1 &&
            flags[i - w].toInt() == 1 && flags[i + w].toInt() == 1 &&
            flags[i - w - 1].toInt() == 1 && flags[i - w + 1].toInt() == 1 &&
            flags[i + w - 1].toInt() == 1 && flags[i + w + 1].toInt() == 1
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    }
}
