package com.mz.floatball

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * 通过老式 CREATE_SHORTCUT 协议对外提供快捷方式。
 * Shortcut Maker、FV悬浮球等应用通过此协议发现并调用。
 * 无需 UI，onCreate 直接返回快捷方式数据后退出。
 */
class ShortcutsCreateActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 快捷方式点击后要执行的 Intent：直接切换输入法（无 UI）
        val shortcutIntent = Intent(this, ShortcutLauncherActivity::class.java).apply {
            action = "com.mz.floatball.SWITCH_IME"
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // 按 CREATE_SHORTCUT 协议返回数据
        val result = Intent()
        result.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
        result.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.shortcut_create_label))
        result.putExtra(
            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
            Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher_foreground)
        )

        setResult(RESULT_OK, result)
        finish()
    }
}
