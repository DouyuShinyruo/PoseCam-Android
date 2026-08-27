package com.posecam.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.posecam.app.camera.CameraScreen
import com.posecam.app.camera.ShutterTrigger
import com.posecam.app.ui.theme.PoseCamTheme
import com.posecam.app.xhs.XhsImportScreen
import com.posecam.app.xhs.XhsShareBus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            PoseCamTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                var clipboardOffer by remember { mutableStateOf<String?>(null) }
                var lastOffered by remember { mutableStateOf<String?>(null) }

                // 回到前台时检测剪贴板里的小红书链接
                // （小红书分享面板是自家定制面板，不走系统分享；主路径 = 复制链接 -> 切回 app）
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            val url = XhsShareBus.readClipboardXhsUrl(this@MainActivity)
                            if (url != null && url != lastOffered &&
                                url != XhsShareBus.sharedUrl
                            ) {
                                clipboardOffer = url
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Box(Modifier.fillMaxSize()) {
                    CameraScreen()

                    // 剪贴板导入提示横幅
                    if (clipboardOffer != null && XhsShareBus.sharedUrl == null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Column(
                                Modifier.padding(
                                    start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp
                                )
                            ) {
                                Text(
                                    "检测到小红书笔记链接",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "要导入这篇笔记的图片作为参考图吗？",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(onClick = {
                                        lastOffered = clipboardOffer
                                        clipboardOffer = null
                                    }) { Text("忽略") }
                                    Button(onClick = {
                                        val url = clipboardOffer
                                        lastOffered = url
                                        clipboardOffer = null
                                        if (url != null) XhsShareBus.sharedUrl = url
                                    }) { Text("导入") }
                                }
                            }
                        }
                    }

                    // 小红书导入页
                    XhsShareBus.sharedUrl?.let { url ->
                        XhsImportScreen(
                            url = url,
                            onClose = {
                                XhsShareBus.sharedUrl = null
                                setIntent(Intent())  // 防止旋转后重新弹出
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND &&
            intent.type?.startsWith("text/") == true
        ) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            XhsShareBus.extractXhsUrl(text)?.let { XhsShareBus.sharedUrl = it }
        }
    }

    /** 音量键 = 快门（相机页在前台时） */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (ShutterTrigger.fire()) return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
