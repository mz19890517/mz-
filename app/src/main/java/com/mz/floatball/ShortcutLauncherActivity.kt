package com.mz.floatball

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.mz.floatball.service.ImeSwitcher

/**
 * 透明 Activity，仅用于快速切换输入法，无任何 UI。
 * 供命令行、桌面快捷方式、三方应用调用。
 */
class ShortcutLauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            ImeSwitcher.toggle(this)
            val toMz = ImeSwitcher.isMzActive(this)
            Toast.makeText(this, if (toMz) "→ Mz 输入法" else "← 已切回", Toast.LENGTH_SHORT).show()

            // 通知悬浮球更新颜色
            sendBroadcast(Intent("com.mz.floatball.IME_CHANGED").apply {
                setPackage(packageName)
                putExtra("is_mz", toMz)
            })
        } catch (e: Exception) {
            Toast.makeText(this, "切换失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        finish()
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
    }
}
