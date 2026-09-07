package com.mz.floatball

import android.content.Context
import android.content.pm.PackageManager

object MzShizuku {
    var available = false; private set

    fun init(context: Context) {
        // 检查 Shizuku App 是否安装
        available = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: Exception) {
            false
        }
        // Shizuku installed: $available
    }

    /** 检查 WRITE_SECURE_SETTINGS 权限是否已授予 */
    fun hasSecurePermission(context: Context): Boolean {
        return context.packageManager.checkPermission(
            "android.permission.WRITE_SECURE_SETTINGS",
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED
    }
}
