#!/bin/bash
# MzFloatBall - 手机端 Android 编译环境一键配置
# 用法: bash setup-build-env.sh [装依赖]
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
MODE="${1:-check}"

echo "=============================================="
echo " MzFloatBall 编译环境配置工具"
echo " 模式: $MODE (check=检查 | install=安装系统依赖)"
echo "=============================================="

# ---------- 系统依赖 ----------
if [ "$MODE" = "install" ]; then
    echo ">> 安装 JDK17 / qemu-user / python3-venv ..."
    apt-get update
    apt-get install -y openjdk-17-jdk-headless qemu-user python3 python3-venv git
fi

# ---------- 检查 JDK ----------
JAVA_HOME_DEFAULT="/usr/lib/jvm/java-17-openjdk-arm64"
if [ -d "$JAVA_HOME_DEFAULT" ]; then
    export JAVA_HOME="$JAVA_HOME_DEFAULT"
    echo "OK: JDK=$JAVA_HOME ($($JAVA_HOME/bin/java -version 2>&1 | head -1))"
else
    JDK=$(dirname "$(dirname "$(readlink -f "$(command -v java 2>/dev/null || echo /nonexistent)")")" 2>/dev/null || echo "")
    export JAVA_HOME="${JDK:-/usr/lib/jvm/default-java}"
    echo "WARN: 未找到 $JAVA_HOME_DEFAULT, 使用 $JAVA_HOME"
    echo "      建议安装: apt install openjdk-17-jdk-headless"
fi

# ---------- 检查 qemu ----------
if command -v qemu-x86_64 >/dev/null; then
    echo "OK: qemu-x86_64"
else
    echo "FAIL: 缺少 qemu-user, 执行: apt install qemu-user (或 bash setup-build-env.sh install)"
fi

# ---------- 部署 AAPT2 包装 ----------
AAPT2_TARGET=/opt/aapt2-custom
if [ -d "$AAPT2_TARGET" ]; then
    echo "OK: AAPT2 已存在 $AAPT2_TARGET"
else
    echo ">> 部署 AAPT2 到 $AAPT2_TARGET ..."
    mkdir -p "$AAPT2_TARGET"
    cp "$DIR/aapt2/aapt2" "$DIR/aapt2/aapt2.orig" "$AAPT2_TARGET/"
    # x86-64 动态库
    rm -rf /opt/x86-64-lib
    mkdir -p /opt/x86-64-lib
    cp -r "$DIR/aapt2/x86-64-lib" /opt/x86-64-lib/usr
    echo "OK: AAPT2 已部署"
fi

# ---------- 检查 Gradle ----------
if [ -x /opt/gradle-8.2/bin/gradle ]; then
    echo "OK: Gradle /opt/gradle-8.2"
else
    echo "WARN: 未找到 Gradle. 可从 https://services.gradle.org/distributions/gradle-8.2-bin.zip 下载解压到 /opt/gradle-8.2"
fi

# ---------- 检查 Android SDK ----------
if [ -d /opt/android-sdk/platforms ]; then
    echo "OK: Android SDK /opt/android-sdk ($(ls /opt/android-sdk/platforms 2>/dev/null | tr '\n' ' '))"
else
    echo "WARN: 未找到 Android SDK. 请参考 README 或从 https://developer.android.com/studio#command-line-tools-only 安装到 /opt/android-sdk"
fi

# ---------- 输出编译命令 ----------
cat << 'END'

==============================================
 编译命令（在项目根目录执行）:
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
   export ANDROID_HOME=/opt/android-sdk
   export PATH=$JAVA_HOME/bin:/opt/gradle-8.2/bin:$PATH
   ./gradlew assembleRelease --no-daemon
   产物: app/build/outputs/apk/release/app-release.apk
==============================================
END
