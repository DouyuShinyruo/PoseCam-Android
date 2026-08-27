package com.posecam.app.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class RefMode(val label: String) {
    ORIGINAL("原图"),
    WIREFRAME("线框")
}

/**
 * 参考图叠加层的状态：位置 / 缩放 / 旋转 / 透明度 / 显示模式。
 * 全部使用 Compose 快照状态，拍摄合成时读取同一份状态保证所见即所得。
 */
class OverlayState {
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
    var rotation by mutableFloatStateOf(0f)
    var alpha by mutableFloatStateOf(0.7f)
    var visible by mutableStateOf(true)
    var mode by mutableStateOf(RefMode.ORIGINAL)

    fun reset() {
        offsetX = 0f
        offsetY = 0f
        scale = 1f
        rotation = 0f
        visible = true
    }

    fun toggleMode() {
        mode = if (mode == RefMode.ORIGINAL) RefMode.WIREFRAME else RefMode.ORIGINAL
    }
}
