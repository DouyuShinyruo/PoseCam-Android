package com.posecam.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.posecam.app.data.AppSettings
import com.posecam.app.history.HistoryScreen
import com.posecam.app.library.MyLibrary
import com.posecam.app.library.LibrarySheet
import com.posecam.app.result.ResultScreen
import com.posecam.app.util.Images
import com.posecam.app.wireframe.EdgeDetector
import com.posecam.app.wireframe.PoseSkeleton
import com.posecam.app.xhs.XhsShareBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }

    // ---------- 权限 ----------
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ---------- 相机与界面状态 ----------
    var lensFront by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var gridOn by remember { mutableStateOf(false) }
    var compositeOn by remember { mutableStateOf(settings.compositeOnCapture) }
    var capturing by remember { mutableStateOf(false) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showLibrary by remember { mutableStateOf(false) }
    var countdownSec by remember { mutableIntStateOf(0) }   // 0=关 / 3 / 10
    var remaining by remember { mutableIntStateOf(0) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var alignPreset by remember { mutableIntStateOf(0) }    // 0=居中 1=左半 2=右半
    var guideShown by remember { mutableStateOf(settings.guideShown) }
    var favorites by remember { mutableStateOf(settings.favorites) }
    var showHistory by remember { mutableStateOf(false) }
    var recentFiles by remember {
        mutableStateOf(
            settings.recentRefs.mapNotNull { k ->
                if (k.startsWith("file:")) {
                    File(k.removePrefix("file:")).takeIf { it.exists() }
                } else {
                    null
                }
            }
        )
    }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    // ---------- 参考图 ----------
    val overlay = remember { OverlayState() }
    var refBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var refWireframe by remember { mutableStateOf<ImageBitmap?>(null) }
    var refSkeleton by remember { mutableStateOf<ImageBitmap?>(null) }
    var refContour by remember { mutableStateOf<ImageBitmap?>(null) }
    var myFiles by remember { mutableStateOf(MyLibrary.list(context)) }

    LaunchedEffect(Unit) {
        overlay.alpha = settings.defaultAlpha
    }
    LaunchedEffect(overlay.alpha) {
        settings.defaultAlpha = overlay.alpha
    }
    LaunchedEffect(compositeOn) {
        settings.compositeOnCapture = compositeOn
    }

    fun setReference(bitmap: Bitmap, key: String) {
        refBitmap = bitmap
        overlay.reset()
        overlay.alpha = settings.defaultAlpha
        settings.lastReference = key
        // 最近使用（最多5张，文件类参与素材库横条展示）
        val recents = (listOf(key) + settings.recentRefs).distinct().take(5)
        settings.recentRefs = recents
        recentFiles = recents.mapNotNull { k ->
            if (k.startsWith("file:")) File(k.removePrefix("file:")).takeIf { it.exists() } else null
        }
    }

    // 线稿生成：参考图或精度变化时重算
    LaunchedEffect(refBitmap) {
        val bitmap = refBitmap ?: return@LaunchedEffect
        refWireframe = null
        val sketch = withContext(Dispatchers.Default) {
            EdgeDetector.toSketch(bitmap)
        }
        refWireframe = sketch.asImageBitmap()
    }

    // 骨架生成：参考图变化时重算（无人则保持 null）
    LaunchedEffect(refBitmap) {
        val bitmap = refBitmap ?: return@LaunchedEffect
        refSkeleton = null
        refContour = null
        val art = withContext(Dispatchers.Default) {
            PoseSkeleton.detect(context, bitmap)
        }
        refSkeleton = art.skeleton?.asImageBitmap()
        refContour = art.contour?.asImageBitmap()
    }

    fun setReferenceFromKey(key: String?) {
        if (key == null) return
        scope.launch(Dispatchers.IO) {
            val bitmap: Bitmap? = when {
                key.startsWith("res:") -> {
                    val resName = key.removePrefix("res:")
                    val resId = context.resources.getIdentifier(
                        resName, "drawable", context.packageName
                    )
                    if (resId != 0) Images.decodeResource(context, resId) else null
                }
                key.startsWith("file:") -> Images.decode(File(key.removePrefix("file:")))
                else -> null
            }
            if (bitmap != null) {
                withContext(Dispatchers.Main) { setReference(bitmap, key) }
            } else {
                withContext(Dispatchers.Main) { toast = "参考图加载失败" }
            }
        }
    }

    // 恢复上次使用的参考图；首次启动预置一张灵感示例
    LaunchedEffect(Unit) {
        setReferenceFromKey(settings.lastReference ?: "res:insp_street_walk")
    }

    // 小红书导入完成后：刷新素材列表 + 自动把最新导入的图设为参考图（闭环）
    LaunchedEffect(XhsShareBus.importedTick) {
        myFiles = MyLibrary.list(context)
        XhsShareBus.latestImportedPath?.let { path ->
            XhsShareBus.latestImportedPath = null
            setReferenceFromKey("file:$path")
        }
    }

    // ---------- 相册选图 ----------
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val imported = MyLibrary.import(context, uri)
                withContext(Dispatchers.Main) {
                    if (imported != null) {
                        myFiles = MyLibrary.list(context)
                        setReferenceFromKey("file:${imported.absolutePath}")
                    } else {
                        toast = "导入失败，请重试"
                    }
                }
            }
        }
    }

    // ---------- 批量导入（灵感库 -> 从相册导入，多选） ----------
    val pickMulti = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                var importedCount = 0
                uris.forEach { uri ->
                    if (MyLibrary.import(context, uri) != null) importedCount++
                }
                withContext(Dispatchers.Main) {
                    myFiles = MyLibrary.list(context)
                    if (importedCount > 0) {
                        MyLibrary.list(context).firstOrNull()?.let { newest ->
                            setReferenceFromKey("file:${newest.absolutePath}")
                        }
                        toast = "已导入 $importedCount/${uris.size} 张，最新一张已设为参考图"
                    } else {
                        toast = "导入失败，请重试"
                    }
                }
            }
        }
    }

    // ---------- CameraX ----------
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(lensFront, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val provider = awaitCameraProvider(context)
            val selector = if (lensFront) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()
                )
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            camera?.cameraControl?.enableTorch(torchOn)
        } catch (e: Exception) {
            toast = "无法启动相机：${e.message}"
        }
    }
    LaunchedEffect(torchOn, camera) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    // 取景器实际像素尺寸，用于把叠加状态映射到照片坐标系
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    // ---------- 拍摄 ----------
    fun takePhoto() {
        if (capturing || !hasCameraPermission) return
        capturing = true
        val photoFile = File(context.cacheDir, "posecam_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val decoded = Images.decode(photoFile, maxDim = 2560)
                                ?: throw IllegalStateException("照片解码失败")
                            val oriented = Images.applyExif(photoFile, decoded)

                            val composed: Bitmap
                            val refBitmapSnapshot = refBitmap
                            if (compositeOn && refBitmapSnapshot != null &&
                                previewSize != IntSize.Zero && overlay.visible
                            ) {
                            val refForMode =
                                when {
                                    overlay.mode != RefMode.WIREFRAME -> refBitmapSnapshot
                                    refContour != null -> refContour!!.asAndroidBitmap()
                                    refSkeleton != null -> refSkeleton!!.asAndroidBitmap()
                                    overlay.mode == RefMode.WIREFRAME && refWireframe != null ->
                                        refWireframe!!.asAndroidBitmap()
                                    else -> refBitmapSnapshot
                                }
                                composed = Composite.draw(
                                    photo = oriented,
                                    reference = refForMode,
                                    overlay = overlay,
                                    previewWidthPx = previewSize.width.toFloat(),
                                    previewHeightPx = previewSize.height.toFloat(),
                                    mirror = lensFront
                                )
                            } else {
                                composed = oriented
                            }

                            val uri = Images.saveToGallery(
                                context,
                                composed,
                                "PoseCam_${System.currentTimeMillis()}.jpg"
                            )
                            photoFile.delete()
                            withContext(Dispatchers.Main) {
                                capturing = false
                                if (uri != null) resultUri = uri else toast = "保存到相册失败"
                            }
                        } catch (e: Exception) {
                            photoFile.delete()
                            withContext(Dispatchers.Main) {
                                capturing = false
                                toast = "拍摄失败：${e.message}"
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    toast = "拍摄失败：${exception.message}"
                }
            }
        )
    }

    /** 快门统一入口：处理倒计时启动/取消，倒计时结束自动拍照 */
    fun handleShutterPress() {
        if (capturing) return
        if (countdownSec > 0) {
            if (remaining > 0) {
                timerJob?.cancel()
                timerJob = null
                remaining = 0
                toast = "已取消倒计时"
            } else {
                remaining = countdownSec
                timerJob = scope.launch {
                    while (remaining > 0) {
                        delay(1000)
                        remaining -= 1
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    timerJob = null
                    takePhoto()
                }
            }
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            takePhoto()
        }
    }

    // 音量键快门
    DisposableEffect(Unit) {
        ShutterTrigger.listener = { handleShutterPress() }
        onDispose { ShutterTrigger.listener = null }
    }

    BackHandler(enabled = resultUri != null) {
        resultUri = null
    }

    // ---------- 布局 ----------
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        if (!hasCameraPermission) {
            PermissionPrompt(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                // 顶部控制区
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Spacer(Modifier.weight(1f))
                    LabeledIcon(
                        icon = Icons.Filled.History,
                        label = "历史",
                        active = false,
                        onClick = { showHistory = true }
                    )
                    LabeledIcon(
                        icon = Icons.Filled.Grid3x3,
                        label = "网格",
                        active = gridOn,
                        onClick = { gridOn = !gridOn }
                    )
                    LabeledIcon(
                        icon = if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        label = "手电筒",
                        active = torchOn,
                        onClick = { torchOn = !torchOn }
                    )
                    LabeledIcon(
                        icon = Icons.Filled.Timer,
                        label = when (countdownSec) {
                            3 -> "3秒"
                            10 -> "10秒"
                            else -> "定时"
                        },
                        active = countdownSec > 0,
                        onClick = {
                            countdownSec = when (countdownSec) {
                                0 -> 3
                                3 -> 10
                                else -> 0
                            }
                            if (remaining > 0) {
                                timerJob?.cancel()
                                timerJob = null
                                remaining = 0
                            }
                            toast = when (countdownSec) {
                                0 -> "倒计时已关闭"
                                else -> "倒计时：${countdownSec}秒后自动拍摄"
                            }
                        }
                    )
                }

                // 参考图透明度
                if (refBitmap != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Opacity,
                            contentDescription = "透明度",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Slider(
                            value = overlay.alpha,
                            onValueChange = { overlay.alpha = it },
                            valueRange = 0.1f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        LabeledIcon(
                            icon = if (overlay.visible) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                            label = if (overlay.visible) "隐藏图" else "显示图",
                            active = overlay.visible,
                            onClick = { overlay.visible = !overlay.visible }
                        )
                        LabeledIcon(
                            icon = Icons.Filled.Compare,
                            label = when (alignPreset) {
                                1 -> "左半"
                                2 -> "右半"
                                else -> "居中"
                            },
                            active = false,
                            onClick = {
                                if (previewSize == IntSize.Zero) {
                                    toast = "取景器还没准备好，稍后再试"
                                } else {
                                    alignPreset = (alignPreset + 1) % 3
                                    overlay.rotation = 0f
                                    overlay.visible = true
                                    when (alignPreset) {
                                        0 -> {
                                            overlay.scale = 1f
                                            overlay.offsetX = 0f
                                            overlay.offsetY = 0f
                                        }
                                        1 -> {
                                            overlay.scale = 0.5f
                                            overlay.offsetX = -previewSize.width * 0.25f
                                            overlay.offsetY = 0f
                                        }
                                        else -> {
                                            overlay.scale = 0.5f
                                            overlay.offsetX = previewSize.width * 0.25f
                                            overlay.offsetY = 0f
                                        }
                                    }
                                }
                            }
                        )
                        LabeledIcon(
                            icon = Icons.Filled.RestartAlt,
                            label = "复位",
                            active = false,
                            onClick = { overlay.reset() }
                        )
                    }
                }

                // 取景器
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                            .onSizeChanged { previewSize = it }
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (gridOn) {
                            GridOverlay(Modifier.fillMaxSize())
                        }
                    }

                    // 参考图叠加层：同几何但不裁剪 —— 可拖到取景框外的黑色区域显示
                    Box(
                        Modifier
                            .fillMaxSize()
                            .aspectRatio(3f / 4f)
                    ) {
                        refBitmap?.let { original ->
                            ReferenceOverlay(
                                overlay = overlay,
                                original = original.asImageBitmap(),
                                wireframe = refWireframe,
                                skeleton = refSkeleton,
                                contour = refContour,
                                onTap = { overlay.toggleMode() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // 状态层（模式标签/拍摄中/倒计时），置于参考图之上
                    Box(
                        Modifier
                            .fillMaxSize()
                            .aspectRatio(3f / 4f)
                    ) {
                        if (refBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xCC1A1A22),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 10.dp)
                            ) {
                                Text(
                                    text = when {
                                        overlay.mode == RefMode.ORIGINAL ->
                                            "原图 · 点按参考图切换"
                                        refContour != null || refSkeleton != null ->
                                            "线框 · 人像模式"
                                        refWireframe == null -> "分析中…"
                                        else -> "线框"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (capturing) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(44.dp)
                            )
                        }

                        if (remaining > 0) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .pointerInput(Unit) {
                                        // 倒计时期间拦截点击，避免误触参考图模式切换
                                        detectTapGestures { }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$remaining",
                                        color = Color.White,
                                        fontSize = 88.sp,
                                        style = MaterialTheme.typography.displayLarge
                                    )
                                    Text(
                                        "点快门取消",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 底部操作区
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                ) {
                    LabeledIcon(
                        icon = Icons.Filled.PhotoLibrary,
                        label = "相册",
                        active = false,
                        onClick = { pickMedia.launch("image/*") }
                    )
                    LabeledIcon(
                        icon = Icons.Filled.Collections,
                        label = "素材库",
                        active = false,
                        onClick = { showLibrary = true }
                    )

                    // 快门
                    Box(
                        Modifier
                            .size(78.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .clickable(enabled = !capturing) { handleShutterPress() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(if (capturing) Color.Gray else Color.White)
                        )
                    }

                    LabeledIcon(
                        icon = Icons.Filled.Cameraswitch,
                        label = "翻转",
                        active = false,
                        onClick = {
                            lensFront = !lensFront
                            torchOn = false
                        }
                    )
                    LabeledIcon(
                        icon = Icons.Filled.Layers,
                        label = "印参考图",
                        active = compositeOn,
                        onClick = {
                            compositeOn = !compositeOn
                            toast = if (compositeOn) {
                                "印参考图开：拍照时会把参考线叠进成片"
                            } else {
                                "印参考图关：拍出干净照片，不叠加参考线"
                            }
                        }
                    )
                }
            }
        }
    }

    // 灵感库弹窗
    if (showLibrary) {
        LibrarySheet(
            myFiles = myFiles,
            recentFiles = recentFiles,
            favorites = favorites,
            onToggleFavorite = { file ->
                val next = favorites.toMutableSet().apply {
                    if (!add(file.absolutePath)) remove(file.absolutePath)
                }
                favorites = next
                settings.favorites = next
            },
            onUseInspiration = { resName ->
                showLibrary = false
                setReferenceFromKey("res:$resName")
            },
            onUseFile = { file ->
                showLibrary = false
                setReferenceFromKey("file:${file.absolutePath}")
            },
            onImport = {
                pickMulti.launch("image/*")
            },
            onPasteLink = {
                val url = XhsShareBus.readClipboardXhsUrl(context)
                if (url != null) {
                    XhsShareBus.sharedUrl = url
                } else {
                    toast = "剪贴板没有小红书链接：请先在小红书笔记页「分享→复制链接」"
                }
            },
            onDeleteFile = { file ->
                MyLibrary.delete(file)
                myFiles = MyLibrary.list(context)
            },
            onDismiss = { showLibrary = false }
        )
    }

    // 首次使用引导
    if (hasCameraPermission && !guideShown && resultUri == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .pointerInput(Unit) {
                    // 拦截底层控件点击，引导未关闭时不可误操作相机
                    detectTapGestures { }
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        "3 步上手 照样相机",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        "① 拖拽 / 双指缩放参考图，滑杆调透明度，一键居中/左半/右半对齐",
                        "② 点按参考图切换 原图 / 线框；线框自动优化：人像显示轮廓+骨架，风景显示线稿",
                        "③ 快门拍照（支持音量键）；右下「印参考图」开关决定照片里是否叠参考线",
                        "④ 小红书导入：笔记页「分享 → 复制链接」，回到 app 自动提示"
                    ).forEach { tip ->
                        Text(
                            tip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            settings.guideShown = true
                            guideShown = true
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("开始使用") }
                }
            }
        }
    }

    // 拍摄结果
    val refOriginalImg = remember(refBitmap) { refBitmap?.asImageBitmap() }
    val compareReference =
        when {
            overlay.mode != RefMode.WIREFRAME -> refOriginalImg
            refContour != null -> refContour
            refSkeleton != null -> refSkeleton
            overlay.mode == RefMode.WIREFRAME && refWireframe != null -> refWireframe
            else -> refOriginalImg
        }
    resultUri?.let { uri ->
        ResultScreen(
            uri = uri,
            reference = compareReference,
            onRetake = { resultUri = null },
            onDone = { resultUri = null }
        )
    }

    // 拍摄历史
    if (showHistory) {
        HistoryScreen(onClose = { showHistory = false })
    }
}

/** 三分法网格辅助线 */
@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color.White.copy(alpha = 0.35f)
        val w = size.width
        val h = size.height
        for (i in 1..2) {
            drawLine(color, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeWidth = 1.5f)
            drawLine(color, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 1.5f)
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(32.dp)
    ) {
        Text(
            "需要相机权限",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "照样相机需要使用相机进行取景拍摄，请在弹窗中允许权限。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text("去授权") }
    }
}

private suspend fun awaitCameraProvider(context: android.content.Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                continuation.resume(future.get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

/** 带文字提示的图标按钮：激活态高亮主题色 */
@Composable
private fun LabeledIcon(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}
