package com.posecam.app.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.posecam.app.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 灵感库 / 我的素材库 底部弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibrarySheet(
    myFiles: List<File>,
    favorites: Set<String>,
    onToggleFavorite: (file: File) -> Unit,
    onUseInspiration: (resName: String) -> Unit,
    onUseFile: (file: File) -> Unit,
    onImport: () -> Unit,
    onPasteLink: () -> Unit,
    onDeleteFile: (file: File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    // 收藏优先，其余按最近使用（导入时间倒序）
    val sortedFiles = remember(myFiles, favorites) {
        myFiles.sortedWith(
            compareByDescending<File> { it.absolutePath in favorites }
                .thenByDescending { it.lastModified() }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("灵感库") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("我的素材") })
            }

            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                ) {
                    InspirationRepo.categories.forEach { category ->
                        category.items.forEach { item ->
                            item(key = item.resName) {
                                val resId = remember(item.resName) {
                                    context.resources.getIdentifier(
                                        item.resName, "drawable", context.packageName
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(onClick = { onUseInspiration(item.resName) })
                                ) {
                                    if (resId != 0) {
                                        Image(
                                            painter = painterResource(resId),
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.75f)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    }
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "已导入 ${myFiles.size} 张参考图",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row {
                            TextButton(onClick = onPasteLink) {
                                Icon(Icons.Filled.Link, contentDescription = null)
                                Spacer(Modifier.padding(start = 4.dp))
                                Text("粘贴链接")
                            }
                            TextButton(onClick = onImport) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.padding(start = 4.dp))
                                Text("相册导入")
                            }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                    ) {
                        if (myFiles.isEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Text(
                                    "还没有素材，点击上方按钮从相册导入",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 32.dp)
                                )
                            }
                        }
                        items(sortedFiles, key = { it.absolutePath }) { file ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { onUseFile(file) },
                                        onLongClick = { deleteTarget = file }
                                    )
                            ) {
                                Box {
                                    FileThumb(file)
                                    Icon(
                                        imageVector = if (file.absolutePath in favorites) {
                                            Icons.Filled.Star
                                        } else {
                                            Icons.Outlined.StarOutline
                                        },
                                        contentDescription = "收藏",
                                        tint = if (file.absolutePath in favorites) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(22.dp)
                                            .clickable { onToggleFavorite(file) }
                                    )
                                }
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "提示：点星标收藏（排最前）· 长按删除",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除素材") },
            text = { Text("确定删除这张参考图吗？") },
            confirmButton = {
                Button(onClick = {
                    onDeleteFile(file)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FileThumb(file: File) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            Images.decode(file, maxDim = 400)
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Spacer(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}
