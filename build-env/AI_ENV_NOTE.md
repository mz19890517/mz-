# 本机 Android 编译环境说明（给其他 AI / 新对话看）

> 本机（Android 手机 + 终端容器）已具备完整 Android 编译环境，可直接编译 APK。

## 环境位置（已安装）

```
JDK17 (arm64):  /usr/lib/jvm/java-17-openjdk-arm64
Gradle 8.2:    /opt/gradle-8.2
Android SDK:   /opt/android-sdk
adb 环境:      /opt/adbenv        （Python adb-shell 虚拟环境）
AAPT2 qemu:    /opt/aapt2-custom  （x86-64 版用 qemu 运行）
```

## 编译任何 Android 项目前，先设置环境变量

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export PATH=$JAVA_HOME/bin:/opt/gradle-8.2/bin:$PATH
```

## 项目 gradle.properties 必须加（关键！）

容器是 ARM64，Gradle 自带的 AAPT2 是 x86-64 无法直接运行，
需要指定 qemu 包装脚本：

```properties
android.aapt2FromMavenOverride=/opt/aapt2-custom/aapt2
org.gradle.jvmargs=-Xmx1536m
```

如果项目没有 android 插件之外的 AAPT2 配置问题，以上足以编译。

## 编译命令

```bash
cd 项目根目录
./gradlew assembleRelease --no-daemon
# 产物: app/build/outputs/apk/release/app-release.apk
```

## 如果环境没装好（新容器/恢复）

```bash
sh /sdcard/AndroidBuildEnv/restore-build-env.sh
```

恢复后环境即与本次一致，无需重新下载。

## 签名（本机项目固定密钥）

密钥: 项目内 `app/mzfloatball-release.jks`
alias: `mzfloatball`，密码: `mzfloatball123`
升级同一应用必须用此密钥，否则需卸载重装。

## 推送安装到手机（可选）

qemu 下原生 adb 不可用，用 Python adb-shell：

```python
from adb_shell.adb_device import AdbDeviceTcp
d = AdbDeviceTcp("手机IP", 5555)
d.connect(rsa_keys=[signer])  # adb key
d.push(open("app-release.apk","rb").read(), "/sdcard/Download/App.apk")
print(d.shell("pm install -r /sdcard/Download/App.apk"))
```

## 其他注意

- 编译已在真实设备环境验证（Android SDK 34 / Gradle 8.2 / JDK17）
- 完整备份在 `/sdcard/AndroidBuildEnv/`（约 1G，6 个 tar.gz + restore 脚本）
- 详细方法论见项目 `skills/android-build-on-device/SKILL.md`
