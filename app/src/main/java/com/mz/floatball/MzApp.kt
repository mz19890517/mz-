package com.mz.floatball

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

class MzApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        MzShizuku.init(this)
        registerShortcut()
    }

    private fun registerShortcut() {
        try {
            val sm = getSystemService(ShortcutManager::class.java) ?: return
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "com.mz.floatball.SWITCH_IME"
                setPackage(packageName)
                putExtra("from_shortcut", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val shortcut = ShortcutInfo.Builder(this, "mz_toggle_ime")
                .setShortLabel(getString(R.string.shortcut_switch_label))
                .setLongLabel(getString(R.string.shortcut_switch_long))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_launcher_foreground))
                .setIntent(intent)
                .build()
            val shortcuts = listOf(shortcut)
            if (!sm.setDynamicShortcuts(shortcuts)) {
                sm.removeDynamicShortcuts(listOf("mz_toggle_ime"))
                sm.addDynamicShortcuts(shortcuts)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        lateinit var instance: MzApp
            private set
    }
}
