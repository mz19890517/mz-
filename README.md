# Mz悬浮球 (MzFloatBall)

Android 悬浮球工具，支持屏幕文本操作和 OCR 文字识别。

## 功能

- ✅ **悬浮球** — 可拖动、自动吸边
- ✅ **复制/粘贴/剪切** — 一键操作当前焦点文本框
- ✅ **全选** — 快速选中全部文本
- 🔲 **OCR 文字识别** — 识别屏幕文字（开发中）

## 权限说明

| 权限 | 用途 |
|------|------|
| 悬浮窗权限 | 在其他 App 上显示悬浮球 |
| 无障碍服务 | 读取/操作当前焦点输入框文本 |

## 技术栈

- Kotlin + AndroidX
- WindowManager (悬浮窗)
- AccessibilityService (文本操作)
- 预留 OCR 模块接口

## 项目结构

```
app/src/main/java/com/mz/floatball/
├── MzApp.kt                          # Application
├── MainActivity.kt                    # 权限引导页面
├── service/
│   ├── FloatBallService.kt            # 悬浮球服务
│   ├── ClipboardHelper.kt            # 剪贴板操作
│   └── ClipboardAccessibilityService.kt # 无障碍服务
└── ocr/
    └── OcrService.kt                 # OCR 预留接口
```

## TODO

- [ ] 接入 PaddleOCR / Google ML Kit 实现真实 OCR
- [ ] 添加翻译功能
- [ ] 添加快捷短语
- [ ] 添加历史剪贴板记录
- [ ] 自定义悬浮球图标
