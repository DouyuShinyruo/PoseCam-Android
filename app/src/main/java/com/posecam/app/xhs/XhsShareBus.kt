package com.posecam.app.xhs

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 小红书分享总线：Activity 接收分享 -> 导入界面 <-> 相机页刷新 */
object XhsShareBus {

    /** 待导入的笔记链接 */
    var sharedUrl by mutableStateOf<String?>(null)

    /** 每完成一次导入 +1，相机页监听它刷新素材列表 */
    var importedTick by mutableIntStateOf(0)

    /** 刚导入成功的最新文件路径，相机页自动设为参考图（闭环） */
    var latestImportedPath by mutableStateOf<String?>(null)

    // 注意：真实分享文本是 markdown 链接格式 [url](url)，且短链域名是 xhslink.cn；
    // URL 字符集用白名单，避免吞进 ]、) 等符号
    private val URL_CHARS = """[A-Za-z0-9\-._~:/?%&=;#@+]*"""

    private val URL_REGEX = Regex(
        """https?://(?:[\w.-]*xiaohongshu\.com/$URL_CHARS|xhslink\.(?:cn|com)/$URL_CHARS)"""
    )

    /** 从分享文本中提取小红书笔记链接（保留 xsec_token 等参数） */
    fun extractXhsUrl(text: String): String? =
        URL_REGEX.find(text)?.value

    /** 读取剪贴板并提取小红书链接（App 前台时调用） */
    fun readClipboardXhsUrl(context: Context): String? {
        return try {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return null
            val clip = manager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
            extractXhsUrl(text)
        } catch (e: Exception) {
            null
        }
    }
}
