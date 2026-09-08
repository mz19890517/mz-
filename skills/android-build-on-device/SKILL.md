---
name: android-build-on-device
description: 在 Android 手机上的终端环境（Termux / PRoot / 容器）里编译 Android 应用，包括 JDK、Gradle、Android SDK、AAPT2 架构兼容、固定签名、推送安装的完整方法。当需要在手机本地构建 APK 或搭建 Android 构建环境时使用。
---

# 在手机上编译 Android 应用

## 环境总览

手机是 **ARM64**，而常用构建工具（Gradle 下载的 AAPT2、部分 SDK 组件）是 **x86-64** 构建，无法直接运行。本方法用 **qemu-user 包装** 解决，无需换手机。

已验证环境（Redmi K20 Pro / 骁龙855，运行 Debian 容器）：

| 组件 | 路径 | 说明 |
|------|------|------|
| JDK | `/usr/lib/jvm/java-17-openjdk-arm64` | 原生 arm64 JDK 17 |
| Gradle | `/opt/gradle-8.2` | 纯 Java，任何架构可运行 |
| Android SDK | `/opt/android-sdk` | build-tools 34.0.0、platforms、platform-tools |
| AAPT2 | `/opt/aapt2-custom/aapt2` | qemu-x86_64 包装脚本 + x86_64 原始二进制 |
| adb 库 | `/opt/adbenv` | Python `adb-shell` 虚拟环境（qemu 下原生 adb server 无法 fork daemon，改用纯 Python 实现） |
| 签名密钥 | `app/mzfloatball-release.jks` | 固定密钥，升级无需卸载重装 |

## 环境变量

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export PATH=$JAVA_HOME/bin:/opt/gradle-8.2/bin:$PATH
```

`local.properties` 指向 SDK：

```
sdk.dir=/opt/android-sdk
```

### Gradle 需要 AAPT2 覆盖

在 `gradle.properties` 中：

```properties
android.aapt2FromMavenOverride=/opt/aapt2-custom/aapt2
```

`/opt/aapt2-custom/aapt2` 内容（qemu 包装）：

```bash
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
exec qemu-x86_64 -L /opt/x86-64-lib/usr "$DIR/aapt2.orig" "$@"
```

依赖：
- `/opt/aapt2-custom/aapt2.orig`：从 Gradle 缓存或 SDK build-tools 拷贝的 **x86-64** 版 aapt2
- `/opt/x86-64-lib/usr`：x86-64 动态库（libc 等），qemu 加载用
- 系统安装 `qemu-user` （qemu-x86_64）

## 编译命令

```bash
cd 项目目录
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export PATH=$JAVA_HOME/bin:/opt/gradle-8.2/bin:$PATH
./gradlew assembleRelease --no-daemon
```

产物：`app/build/outputs/apk/release/app-release.apk`

> 已签名的 release（`app/mzfloatball-release.jks`，密码/别名 `mzfloatball`）会同时打出。首次在无缓存环境编译约 2-5 分钟，之后增量约 1-2 分钟。

## 固定签名（重要）

`app/build.gradle.kts` 中已配置，**永远不要更换**：

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("mzfloatball-release.jks")
        storePassword = "mzfloatball123"
        keyAlias = "mzfloatball"
        keyPassword = "mzfloatball123"
    }
}
```

签名固定后：升级安装是"覆盖安装"，不需要卸载；换了密钥则必须卸载重装且数据丢失。

## 推送安装到手机

qemu 环境下原生 `adb` server 无法正常 fork daemon，改用 Python `adb-shell` 库（通过 TCP 连接手机的 adb 调试）：

```python
from adb_shell.adb_device import AdbDeviceTcp

device = AdbDeviceTcp('手机IP', 5555, default_transport_timeout_s=8)
device.connect(rsa_keys=[signer], auth_timeout_s=5)  # signer 为 adb key
with open('app-release.apk', 'rb') as f:
    device.push(f.read(), '/sdcard/Download/App.apk')
print(device.shell('pm install -r /sdcard/Download/App.apk'))
```

启用方式（手机端）：
1. 手机打开「开发者选项 → USB/无线调试」
2. 用 `adb pair` 配对（首次），或直接 `adb connect` 同屏软件（甲壳虫等）先建立信任
3. 记下 IP:5555

## 常见坑

| 问题 | 原因 | 解决 |
|------|------|------|
| `AAPT2 aapt2-*.jar 无法运行` | x86-64 二进制在 arm64 上直接执行失败 | 用 `android.aapt2FromMavenOverride` 指向 qemu 包装 |
| `SDK location not found` | `ANDROID_HOME` 或 `local.properties` 缺失 | 设置环境变量 + `sdk.dir` |
| `Unable to fork adb server` | qemu 下原生 adb server bug | 用 Python adb-shell 库 |
| 第一次编译很慢 | 下载依赖/AAPT2 | 正常，之后有 `.gradle` 缓存 |
| 内存不足 | Gradle daemon 占用高 | 用 `--no-daemon` + 减小 `org.gradle.jvmargs` |
| 推送超时 | 手机 WiFi 网络变化 | 重新确认 IP，`adb pair` 重新建立 |

## 完整环境重建（新设备）

一般只需三步：

1. 安装系统包：`apt install openjdk-17-jdk qemu-user`、Python3 + venv
2. 解压本仓库 `build-env/`（含 aapt2 包装与配置脚本）：按 `build-env/README.md` 执行 `setup-build-env.sh`
3. 配置 `local.properties` 指向 SDK，执行编译命令

详细脚本与二进制见仓库 `build-env/` 目录。
