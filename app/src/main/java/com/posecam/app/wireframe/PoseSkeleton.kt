package com.posecam.app.wireframe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlin.math.hypot
import kotlin.math.max

/**
 * 骨架模式：MediaPipe Pose Landmarker（33 个人体关键点，端侧离线）-> 火柴人骨架图。
 * 未检测到人物时返回 null（界面自动回退并提示）。
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
                    .build()
            ).also { landmarker = it }
        }

    /** 检测并渲染骨架；图片无人时返回 null */
    fun detectAndRender(context: Context, source: Bitmap): Bitmap? {
        val bitmap = scaleDown(source, 1400)
        return try {
            val result = detector(context).detect(BitmapImageBuilder(bitmap).build())
            val landmarks = result.landmarks().firstOrNull() ?: return null
            if (landmarks.isEmpty()) return null
            renderSkeleton(bitmap.width, bitmap.height, landmarks)
        } catch (e: Exception) {
            null
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
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
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

        // 头部圆圈：中心取双耳中点（缺失则用鼻子），半径按肩宽比例
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
        return out
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
