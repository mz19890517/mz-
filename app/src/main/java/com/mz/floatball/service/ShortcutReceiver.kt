package com.mz.floatball.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ShortcutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.mz.floatball.SWITCH_IME") {
            ImeSwitcher.toggle(context)
            val toMz = ImeSwitcher.isMzActive(context)
            AppLog.log("ShortcutReceiver", "toggle: toMz=$toMz")
            context.sendBroadcast(Intent("com.mz.floatball.IME_CHANGED").apply {
                setPackage(context.packageName)
                putExtra("is_mz", toMz)
            })
        }
    }
}
