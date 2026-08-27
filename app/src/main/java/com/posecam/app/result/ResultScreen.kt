package com.posecam.app.result

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.posecam.app.util.CompareImage
import com.posecam.app.util.Images
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 拍摄结果全屏页。
 * 直接叠加在主窗口内容之上，而不是用系统 Dialog ——
 * Dialog 全屏窗口在部分机型上不处理系统栏 inset，会导致底部按钮超出屏幕。
 */
@Composable
fun ResultScreen(
    uri: Uri,
    reference: ImageBitmap?,
    onRetake: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var compare by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { Images.decode(context, uri, maxDim = 2160) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // 顶栏：保存提示 + 关闭
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "已保存到相册 · Pictures/PoseCam",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDone) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }

            // 对比模式切换（有参考图时可用）
            if (reference != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = !compare,
                        onClick = { compare = false },
                        label = { Text("成片") }
                    )
                    Spacer(Modifier.size(10.dp))
                    FilterChip(
                        selected = compare,
                        onClick = { compare = true },
                        label = { Text("和参考图对比") }
                    )
                    Spacer(Modifier.size(10.dp))
                    TextButton(
                        enabled = bitmap != null,
                        onClick = {
                            val photo = bitmap ?: return@TextButton
                            val ref = reference ?: return@TextButton
                            scope.launch(Dispatchers.IO) {
                                val compareShot = CompareImage.create(
                                    reference = ref.asAndroidBitmap(),
                                    photo = photo
                                )
                                val saved = Images.saveToGallery(
                                    context,
                                    compareShot,
                                    "PoseCompare_${System.currentTimeMillis()}.jpg"
                                )
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (saved != null) "对比图已保存到相册" else "保存失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("存对比图")
                    }
                }
            }

            // 照片预览：占满剩余空间，加载中显示进度圈
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (compare && reference != null) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                Image(
                                    bitmap = reference,
                                    contentDescription = "参考图",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                )
                            }
                            Text(
                                "参考图",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap!!.asImageBitmap(),
                                        contentDescription = "成片",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                            Text(
                                "成片",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    bitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "拍摄结果",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp)
                        )
                    } ?: CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                    )
                }
            }

            // 底部操作：固定高度，保证任何屏幕都完整可见
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("重拍")
                }
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "分享照片"))
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("分享")
                }
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.height(48.dp)
                ) { Text("完成") }
            }
        }
    }
}
