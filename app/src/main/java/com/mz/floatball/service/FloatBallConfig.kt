package com.mz.floatball.service

import android.content.Context

class FloatBallConfig(context: Context) {
    private val prefs = context.getSharedPreferences("floatball_config", Context.MODE_PRIVATE)

    var ballAlpha: Float
        get() = prefs.getFloat("ball_alpha", 1.0f).coerceIn(0.1f, 1.0f)
        set(v) { prefs.edit().putFloat("ball_alpha", v.coerceIn(0.1f, 1.0f)).apply() }

    var snapDelayMs: Long
        get() = prefs.getLong("snap_delay_ms", 300).coerceIn(0, 3000)
        set(v) { prefs.edit().putLong("snap_delay_ms", v.coerceIn(0, 3000)).apply() }

    var showPanelOnTap: Boolean
        get() = prefs.getBoolean("show_panel_on_tap", true)
        set(v) { prefs.edit().putBoolean("show_panel_on_tap", v).apply() }
}
