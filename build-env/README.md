# build-env — 手机端编译依赖包

本目录包含在 Android 手机（ARM64）上编译本项目的完整依赖。

## 内容

| 路径 | 说明 |
|------|------|
| `aapt2/aapt2` | qemu-x86_64 包装脚本（启动 `aapt2.orig`） |
| `aapt2/aapt2.orig` | x86-64 AAPT2 原始二进制（Gradle 下载的版本无法在 ARM64 直接运行） |
| `aapt2/x86-64-lib/` | x86-64 动态库（`qemu-x86_64 -L` 加载用） |
| `setup-build-env.sh` | 一键检查/部署：复制 AAPT2 到 `/opt/aapt2-custom`、检查 JDK/Gradle/SDK |

## 快速开始（新设备）

```bash
# 1. 安装系统依赖（root / Termux proot）
apt install openjdk-17-jdk-headless qemu-user python3 python3-venv git unzip

# 2. 部署本目录
bash build-env/setup-build-env.sh install

# 3. 安装 Android SDK 到 /opt/android-sdk（含 platforms;android-34, build-tools;34.0.0）
# 4. 下载 Gradle 8.2 解压到 /opt/gradle-8.2
# 5. 构建
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export PATH=$JAVA_HOME/bin:/opt/gradle-8.2/bin:$PATH
./gradlew assembleRelease --no-daemon
```

## 为什么需要 qemu 包装 AAPT2

容器是 ARM64，但 Gradle 拉取的 AAPT2 是 x86-64 构建，无法直接执行。
`qemu-user`（`qemu-x86_64`）可以透明翻译执行 x86-64 ELF，配合 `-L` 指定 x86-64 动态库路径。

```bash
# /opt/aapt2-custom/aapt2 内容：
#!/bin/bash
exec qemu-x86_64 -L /opt/x86-64-lib/usr /opt/aapt2-custom/aapt2.orig "$@"
```

`gradle.properties` 中启用：

```properties
android.aapt2FromMavenOverride=/opt/aapt2-custom/aapt2
```
