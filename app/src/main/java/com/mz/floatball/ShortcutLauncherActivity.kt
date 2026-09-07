package com.mz.floatball

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.mz.floatball.service.ImeSwitcher

/**
 * 透明 Activity，专门用于被三方应用（FV悬浮球等）通过 intent 调用。
 * 不显示任何 UI，直接切换输入法后退出。
 */
class ShortcutLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ImeSwitcher.toggle(this)
        val toMz = ImeSwitcher.isMzActive(this)
        
        Toast.makeText(this, if (toMz) "→ Mz 输入法" else "← 已切回", Toast.LENGTH_SHORT).show()
        
        // 通知悬浮球更新颜色
        sendBroadcast(Intent("com.mz.floatball.IME_CHANGED").apply {
            setPackage(packageName)
            putExtra("is_mz", toMz)
        })
        
        finish()
    }
}
