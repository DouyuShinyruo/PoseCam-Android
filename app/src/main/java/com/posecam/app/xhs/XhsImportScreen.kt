package com.posecam.app.xhs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.posecam.app.library.MyLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** 桌面 Chrome UA：避免移动端被跳转 App / 登录墙 */
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private const val XHS_REFERER = "https://www.xiaohongshu.com/"

/** 从 DOM 提取笔记内容图（CDN 域名过滤 + 去头像图标 + 去重） */
private const val EXTRACT_JS = """
(function(){
  var out = [];
  var imgs = document.querySelectorAll('img');
  for (var i = 0; i < imgs.length; i++) {
    var img = imgs[i];
    var src = img.currentSrc || img.src || img.getAttribute('data-src') || '';
    if (!src || src.indexOf('data:') === 0) continue;
    if (src.indexOf('xhscdn.com') === -1 && src.indexOf('xiaohongshu.com') === -1) continue;
    if (/avatar|icon|logo|emoji|placeholder|default|prepost/i.test(src)) continue;
    out.push(src);
  }
  var seen = {};
  var uniq = [];
  for (var j = 0; j < out.length; j++) {
    if (!seen[out[j]]) { seen[out[j]] = 1; uniq.push(out[j]); }
  }
  return uniq;
})()
"""

/**
 * 小红书导入（方案C）：内置浏览器打开笔记 -> 自动滚动触发懒加载 ->
 * JS 提取 DOM 图片 -> 缩略图勾选 -> 下载进"我的素材"。
 */
@Composable
fun XhsImportScreen(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageFinished by remember { mutableStateOf(false) }
    var foundUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("加载笔记中…") }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = DESKTOP_UA
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                    pageFinished = true
                    status = "正在滚动加载图片…"
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val target = request?.url?.toString() ?: return false
                    // 拦截"打开App"等私有协议，保持在网页内
                    return target.startsWith("xhsdiscover://") ||
                        target.startsWith("weixin://")
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }
    LaunchedEffect(url) {
        pageFinished = false
        foundUrls = emptyList()
        webView.loadUrl(url)
    }

    // 自动滚动 + 分批提取（懒加载图片需要滚动触发）
    LaunchedEffect(pageFinished, url) {
        if (!pageFinished) return@LaunchedEffect
        repeat(10) {
            delay(700)
            webView.evaluateJavascript(
                "window.scrollBy(0, Math.round(window.innerHeight * 0.8));",
                null
            )
            delay(400)
            webView.evaluateJavascript(EXTRACT_JS) { json ->
                val parsed = parseUrls(json)
                if (parsed.isNotEmpty()) {
                    val merged = (foundUrls + parsed).distinct()
                    if (merged.size > foundUrls.size) {
                        foundUrls = merged
                        // 默认全选：整篇笔记的图基本都要
                        selected = merged.toSet()
                    }
                }
            }
        }
        status = if (foundUrls.isEmpty()) {
            "未检测到图片：可能需要先在页面内登录，或手动浏览一下页面"
        } else {
            "检测到 ${foundUrls.size} 张图（可在页面继续滚动后重新点「重新检测」）"
        }
    }

    fun importSelected() {
        if (selected.isEmpty() || importing) return
        importing = true
        val urls = selected.toList()
        scope.launch(Dispatchers.IO) {
            var ok = 0
            urls.forEach { u ->
                if (MyLibrary.importFromUrl(context, u, DESKTOP_UA, XHS_REFERER) != null) {
                    ok++
                }
            }
            withContext(Dispatchers.Main) {
                importing = false
                XhsShareBus.importedCount += 1
                Toast.makeText(
                    context,
                    "已导入 $ok/${urls.size} 张到我的素材",
                    Toast.LENGTH_SHORT
                ).show()
                onClose()
            }
        }
    }

    BackHandler { onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // 顶栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "从小红书导入",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                enabled = !importing,
                onClick = {
                    status = "重新检测中…"
                    webView.evaluateJavascript(EXTRACT_JS) { json ->
                        val merged = (foundUrls + parseUrls(json)).distinct()
                        foundUrls = merged
                        selected = merged.toSet()
                        status = if (merged.isEmpty()) "仍未检测到图片" else "检测到 ${merged.size} 张图"
                    }
                }
            ) { Text("重新检测") }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
        Text(
            "提示：部分笔记需登录后才能看全（登录一次会记住）；页面可手动滚动/缩放",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // 网页
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (!pageFinished) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                )
            }
        }

        // 底部选择面板
        if (foundUrls.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "已选 ${selected.size}/${foundUrls.size} 张",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = selected.isNotEmpty() && !importing,
                        onClick = { importSelected() }
                    ) {
                        if (importing) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(Icons.Filled.Download, contentDescription = null)
                        }
                        Spacer(Modifier.size(4.dp))
                        Text(if (importing) "导入中…" else "导入选中")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(foundUrls, key = { it }) { imageUrl ->
                        NetworkThumb(
                            url = imageUrl,
                            selected = imageUrl in selected,
                            onClick = {
                                selected = if (imageUrl in selected) {
                                    selected - imageUrl
                                } else {
                                    selected + imageUrl
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkThumb(url: String, selected: Boolean, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", DESKTOP_UA)
                if (connection.responseCode in 200..299) {
                    val bytes = connection.inputStream.readBytes()
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        Modifier
            .size(86.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            )
        }
    }
}

private fun parseUrls(result: String?): List<String> {
    if (result == null || result == "null") return emptyList()
    return try {
        val array = JSONArray(result)
        (0 until array.length()).mapNotNull { i ->
            array.optString(i).takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
