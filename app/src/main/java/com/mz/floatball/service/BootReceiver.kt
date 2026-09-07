package com.mz.floatball.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AppLog.log("Boot", "开机自启动")
            context.startForegroundService(Intent(context, FloatBallService::class.java))
        }
    }
}
