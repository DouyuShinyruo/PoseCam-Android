package com.posecam.app.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * 参考图叠加层：
 * - 单指拖拽 / 双指缩放旋转（自由变换）
 * - 单指轻点：切换 原图 <-> 线框 模式
 * - 透明度、显示隐藏由 OverlayState 控制
 */
@Composable
fun ReferenceOverlay(
    overlay: OverlayState,
    original: ImageBitmap,
    wireframe: ImageBitmap?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = if (overlay.mode == RefMode.WIREFRAME && wireframe != null) wireframe else original

    // 参考图隐藏时不接管手势，点按取景器不会误触模式切换
    val gestureModifier = if (overlay.visible) {
        Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startTime = down.uptimeMillis
                    var moved = false
                    var multiTouch = false
                    var endTime = startTime

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.size > 1) multiTouch = true

                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val rotation = event.calculateRotation()

                        if (abs(pan.x) > 3f || abs(pan.y) > 3f || zoom != 1f || rotation != 0f) {
                            moved = true
                        }

                        overlay.offsetX = (overlay.offsetX + pan.x)
                            .coerceIn(-size.width * 0.95f, size.width * 0.95f)
                        overlay.offsetY = (overlay.offsetY + pan.y)
                            .coerceIn(-size.height * 0.95f, size.height * 0.95f)
                        overlay.scale = (overlay.scale * zoom).coerceIn(0.15f, 10f)
                        overlay.rotation += rotation

                        event.changes.forEach { change ->
                            if (change.pressed &&
                                (change.positionChange().x != 0f || change.positionChange().y != 0f)
                            ) {
                                change.consume()
                            }
                            if (!change.pressed) endTime = maxOf(endTime, change.uptimeMillis)
                        }

                        if (event.changes.none { it.pressed }) break
                    }

                    val duration = endTime - startTime
                    if (!moved && !multiTouch && duration in 0..350) {
                        onTap()
                    }
                }
            }
    } else {
        Modifier
    }

    Box(
        modifier
            .fillMaxSize()
            .then(gestureModifier)
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "参考图（点击切换线框）",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = overlay.offsetX
                    translationY = overlay.offsetY
                    scaleX = overlay.scale
                    scaleY = overlay.scale
                    rotationZ = overlay.rotation
                    alpha = if (overlay.visible) overlay.alpha else 0f
                    transformOrigin = TransformOrigin.Center
                }
        )
    }
}
