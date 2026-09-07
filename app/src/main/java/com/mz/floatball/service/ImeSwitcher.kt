package com.mz.floatball.service

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

object ImeSwitcher {

    const val MZ_IME_ID = "com.mz.floatball/.service.MzInputMethodService"

    private const val KEY_SAVED_IME = "saved_ime"
    private const val PREF_NAME = "ime_state"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getCurrentIme(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
    }

    fun isMzActive(context: Context): Boolean {
        return getCurrentIme(context).contains("com.mz.floatball")
    }

    /** 检查 Mz 输入法是否已启用 */
    fun isMzImeEnabled(context: Context): Boolean {
        val ims = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return ims.enabledInputMethodList.any { it.id == MZ_IME_ID }
    }

    /** 检查是否已获得自动切换权限 */
    fun hasSecurePermission(context: Context): Boolean {
        return context.packageManager.checkPermission(
            "android.permission.WRITE_SECURE_SETTINGS", context.packageName
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** 切换到 Mz 输入法（先保存当前） */
    fun switchToMz(context: Context): Boolean {
        val current = getCurrentIme(context)
        if (isMzActive(context)) return false // 已经是 Mz
        prefs(context).edit().putString(KEY_SAVED_IME, current).apply()
        AppLog.log("ImeSwitcher", "保存当前: $current, 切到 Mz")
        return Settings.Secure.putString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, MZ_IME_ID)
    }

    /** 切回之前的输入法 */
    fun switchBack(context: Context): Boolean {
        val saved = prefs(context).getString(KEY_SAVED_IME, "") ?: ""
        if (saved.isEmpty() || saved == MZ_IME_ID) return false
        AppLog.log("ImeSwitcher", "切回: $saved")
        val ok = Settings.Secure.putString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, saved)
        if (ok) prefs(context).edit().remove(KEY_SAVED_IME).apply()
        return ok
    }

    /** 智能切换：非 Mz → 切到 Mz；是 Mz → 切回 */
    fun toggle(context: Context): Boolean {
        return if (isMzActive(context)) switchBack(context) else switchToMz(context)
    }
}
