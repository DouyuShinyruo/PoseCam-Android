package com.posecam.app.camera

/** 音量键快门触发器：Activity 按键 -> 相机页快门逻辑（单向、可空，避免泄漏） */
object ShutterTrigger {
    @Volatile
    var listener: (() -> Unit)? = null

    /** @return true 表示已消费此次按键 */
    fun fire(): Boolean {
        val l = listener ?: return false
        l()
        return true
    }
}
