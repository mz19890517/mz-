---
name: android-shortcut-publish
description: 让 Android 应用快捷方式可被 Shortcut Maker、FV 悬浮球、Tasker 等三方应用发现并调用。核心是 CREATE_SHORTCUT 老式协议 + 动态/静态快捷方式。当应用需要对外提供"一键调用"快捷方式、或遇到三方工具列表里找不到本应用快捷方式时使用。
---

# Android 快捷方式发布（让三方应用可发现）

## 结论先行

要让 **Shortcut Maker / FV 悬浮球 / 快捷方式管理类应用**在"添加快捷方式"列表里看到你的应用，只注册现代快捷方式（`android.app.shortcuts` / `setDynamicShortcuts`）**不够**。这些工具普遍通过**老式 `ACTION_CREATE_SHORTCUT` 协议**枚举可用的应用。

学习案例：SearchEVO（搜索进化）在 Shortcut Maker 中显示为「调用EVO功能」，其原理就是注册了一个响应 `CREATE_SHORTCUT` 的 Activity，label 就是显示名。

## 三种快捷方式机制对比

| 机制 | 声明方式 | 谁能看到 | 长按图标 | Shortcut Maker/FV |
|------|---------|---------|---------|------------------|
| 现代静态 | `res/xml/shortcuts.xml` + manifest `meta-data` | Launcher | ✅ | ❌ |
| 现代动态 | `ShortcutManager.setDynamicShortcuts()` | Launcher | ✅ | ❌ |
| 老式协议 | Activity 注册 `ACTION_CREATE_SHORTCUT` | 任意支持该协议的应用 | ❌ | ✅ |

**想两边都覆盖：三者都要做。**

## 实现步骤（老式协议，核心）

### 1. 创建响应 `CREATE_SHORTCUT` 的 Activity

无需任何 UI，`onCreate` 中组装快捷方式数据、`setResult` 后立即 `finish`。

```kotlin
class ShortcutsCreateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 快捷方式点击后执行的 Intent（指向真正处理动作的组件，无 UI 更好）
        val shortcutIntent = Intent(this, ShortcutLauncherActivity::class.java).apply {
            action = "com.example.YOUR_ACTION"
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // CREATE_SHORTCUT 协议返回数据
        val result = Intent()
        result.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
        result.putExtra(Intent.EXTRA_SHORTCUT_NAME, "快捷方式显示名")  // 显示在列表里
        result.putExtra(
            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
            Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher_foreground)
        )

        setResult(RESULT_OK, result)
        finish()
    }
}
```

### 2. Manifest 注册（关键）

```xml
<activity
    android:name=".ShortcutsCreateActivity"
    android:label="应用显示名（Shortcut Maker 列表里显示的名字）"
    android:exported="true"
    android:theme="@style/Theme.Transparent"   <!-- 透明主题，无 UI 闪现 -->
    android:excludeFromRecents="true"
    android:noHistory="true">
    <intent-filter>
        <action android:name="android.intent.action.CREATE_SHORTCUT" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### 3. 透明主题（避免 Activity 白屏闪烁）

`res/values/themes_shortcut.xml`：

```xml
<style name="Theme.Transparent" parent="@android:style/Theme.Translucent.NoTitleBar">
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowAnimationStyle">@null</item>
</style>
```

### 4. 同时保留现代快捷方式（长按图标可用）

动态注册（App 启动时调用）：

```kotlin
val sm = getSystemService(ShortcutManager::class.java) ?: return
val intent = Intent(this, ShortcutLauncherActivity::class.java).apply {
    action = "com.example.YOUR_ACTION"
    setPackage(packageName)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
val shortcut = ShortcutInfo.Builder(this, "unique_id")
    .setShortLabel("短名")
    .setLongLabel("长名")
    .setIcon(Icon.createWithResource(this, R.drawable.ic_launcher_foreground))
    .setIntent(intent)
    .build()
val shortcuts = listOf(shortcut)
if (!sm.setDynamicShortcuts(shortcuts)) {
    sm.removeDynamicShortcuts(listOf("unique_id"))
    sm.addDynamicShortcuts(shortcuts)   // 失败回退
}
// 强制刷新系统索引（升级安装后系统可能不自动刷新）
sm.updateShortcuts(shortcuts)
```

静态注册（`res/xml/shortcuts.xml`）：

```xml
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut
        android:shortcutId="unique_id"
        android:shortcutShortLabel="@string/short_label"
        android:shortcutLongLabel="@string/long_label"
        android:enabled="true"
        android:icon="@drawable/ic_launcher_foreground">
        <intent
            android:action="com.example.YOUR_ACTION"
            android:targetPackage="com.example.package"
            android:targetClass="com.example.package.ShortcutLauncherActivity" />
    </shortcut>
</shortcuts>
```

Manifest 中 MainActivity（带 LAUNCHER 的那个）里加 meta-data：

```xml
<meta-data
    android:name="android.app.shortcuts"
    android:resource="@xml/shortcuts" />
```

## 关键细节

- `EXTRA_SHORTCUT_INTENT` 里的 Intent 建议指向**无 UI 的透明 Activity**：调用时不会打开应用首页，切换动作完成后自动消失。
- `android:label` 就是 Shortcut Maker / FV 列表里显示的名字。
- 图标用 `Intent.ShortcutIconResource.fromContext()`（不是 Bitmap），稳定且省内存。
- `setPackage(packageName)` + `FLAG_ACTIVITY_NEW_TASK` 可让快捷方式跨应用正常启动。

## 验证

1. 安装 APK 后打开 **Shortcut Maker**，在"应用/快捷方式"页应看到你的应用（label 名）。
2. 选择它 → 创建快捷方式 → 点击快捷方式，应触发目标动作且不弹出应用首页。
3. 长按桌面应用图标，应出现快捷方式（现代快捷方式生效）。
4. 验证 APK 声明：`aapt2 dump xmltree --file AndroidManifest.xml app.apk | grep -B5 CREATE_SHORTCUT`

## 排查

- **Shortcut Maker 里看不到**：确认 `CREATE_SHORTCUT` Activity 已 `exported=true`；重启 Shortcut Maker（它可能缓存应用列表）；必要时重启手机。
- **MIUI 长按看不到**：现代快捷方式被桌面缓存，执行 `adb shell pm clear com.miui.home` 或卸载重装。
- **调用后打开首页**：`EXTRA_SHORTCUT_INTENT` 指向了带 UI 的 MainActivity，改为指向透明处理 Activity。
- **图标不显示**：部分工具需要 `EXTRA_SHORTCUT_ICON_RESOURCE` 而非 Bitmap。
