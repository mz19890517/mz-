package com.mz.floatball.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class LogWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var rootView: View? = null
    private var textView: TextView? = null
    private var visible = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        instance = this
    }

    fun showLog() {
        if (visible) return

        textView = TextView(this).apply {
            text = AppLog.dump().ifEmpty { "（暂无日志）" }
            setTextColor(Color.GREEN)
            textSize = 11f
            setPadding(4, 8, 4, 4)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val scroll = ScrollView(this).apply {
            addView(textView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val btnClose = Button(this).apply {
            text = "关闭"
            setOnClickListener { hideLog() }
        }
        val btnCopy = Button(this).apply {
            text = "复制全部日志"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("log", AppLog.dump()))
                Toast.makeText(this@LogWindowService, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(btnClose)
        btnRow.addView(btnCopy)

        val title = TextView(this).apply {
            text = "MzFloatBall 日志"
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(230, 30, 30, 30))
            setPadding(8, 8, 8, 8)
            addView(title)
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(btnRow)
        }
        rootView = root

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.6).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(root, params)
        AppLog.listener = { line ->
            textView?.post { textView?.append("\n$line") }
        }
        visible = true
    }

    fun hideLog() {
        if (!visible) return
        AppLog.listener = null
        rootView?.let { windowManager.removeView(it) }
        rootView = null
        visible = false
    }

    override fun onDestroy() {
        hideLog()
        instance = null
        super.onDestroy()
    }

    companion object {
        var instance: LogWindowService? = null
            private set
    }
}
