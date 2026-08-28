# 照样相机 PoseCam（Android）

[![Release Build](https://github.com/DouyuShinyruo/PoseCam-Android/actions/workflows/release.yml/badge.svg)](https://github.com/DouyuShinyruo/PoseCam-Android/actions/workflows/release.yml)

**照着参考图，拍出同款。** 一款"能贴参考图的相机"：把姿势/构图参考图叠在取景器上，
随手拍出同款照片。个人学习项目，灵感来自 iOS 上的同类产品（详见 PLAN.md 调研部分）。

## 功能

- 相机取景：前/后摄、手电筒、三分网格、拍摄倒计时（3s/10s）、音量键快门
- 参考图叠加：拖拽 / 双指缩放旋转 / 透明度 / 隐藏 / 复位 / 一键 居中·左半·右半 对齐
- **智能线框**（点按参考图切换 原图/线框）：自动路由——人像显示 分割轮廓+火柴人骨架（v0.5.1），
  风景/物体显示 Canny 线稿（自动去纹理噪声，三档精度）；全部端侧离线
- 线框精度三档：简洁 / 标准 / 精细（线框模式点顶部标签切换）
- 内置灵感库（街拍/户外/美食/多人/自拍/室内，12 张离线模板）+ 我的素材库
- 素材批量导入、星标收藏（收藏优先 + 最近使用排序）
- **小红书一键导入**：笔记页「分享 → 复制链接」→ 切回照样相机自动弹出导入提示
  （或素材库点「粘贴链接」），自动打开笔记提取全部图片，勾选后直接进素材库
- 拍照自动把参考图合成进照片（可开关），保存到相册 `Pictures/PoseCam`
- 结果页：成片 / 参考图左右对比视图；一键生成「参考 vs 成片」对比分享图
- 首次启动有 3 步上手引导

## 运行

1. 用 **Android Studio** 打开本文件夹（File → Open）
2. 等待 Gradle Sync 完成
3. 连接手机（开启 USB 调试）→ Run ▶

要求：JDK 17+，Android SDK 36。

## 构建两种 APK

```powershell
.\gradlew.bat assembleDebug     # 调试版 app/build/outputs/apk/debug/
.\gradlew.bat assembleRelease   # 签名正式版 app/build/outputs/apk/release/（约 39MB）
```

签名信息在项目根目录 `keystore.properties`（已 gitignore），密钥库为 `app/pose-release.jks`。
**注意保管这两个文件：丢失后无法给同一应用身份发更新。**

## 结构

```text
app/src/main/java/com/posecam/app/
├── MainActivity.kt           入口（音量键快门）
├── camera/
│   ├── CameraScreen.kt       相机主界面（取景/控制/拍摄/倒计时）
│   ├── ReferenceOverlay.kt   参考图叠加层（手势变换）
│   ├── OverlayState.kt       叠加状态
│   ├── Composite.kt          拍照合成
│   └── ShutterTrigger.kt     音量键快门触发器
├── wireframe/EdgeDetector.kt 线框模式（Canny 风格流水线）
├── wireframe/PoseSkeleton.kt  骨架模式（MediaPipe Pose）
├── library/                  灵感库 + 我的素材库（收藏/批量导入）
├── result/ResultScreen.kt    成片预览/对比/分享
├── data/AppSettings.kt       设置持久化
└── util/                     图片解码/EXIF/相册保存/对比拼图
```

详细计划见 [PLAN.md](PLAN.md)。
