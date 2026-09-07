package com.mz.floatball

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mz.floatball.service.FloatBallService
import com.mz.floatball.service.ImeSwitcher

class MainActivity : AppCompatActivity() {

    private lateinit var tvImeStatus: TextView
    private lateinit var tvPermStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvImeStatus = TextView(this).apply { textSize = 16f; setPadding(0, 16, 0, 8) }
        tvPermStatus = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, 8) }

        val root = findViewById<LinearLayout>(R.id.mainLayout)

        // 先隐藏默认的 status/buttons
        findViewById<TextView>(R.id.tvStatus).visibility = android.view.View.GONE
        findViewById<Button>(R.id.btnOverlay).visibility = android.view.View.GONE
        findViewById<Button>(R.id.btnAccessibility).visibility = android.view.View.GONE

        root.addView(tvImeStatus)
        root.addView(tvPermStatus)

        // 提示：需要设置 Mz 为可用输入法
        root.addView(TextView(this).apply {
            text = "首次使用请到「设置 → 管理键盘」开启 Mz 悬浮球输入法"
            textSize = 13f; setTextColor(0xFF888888.toInt()); setPadding(0, 0, 0, 12)
        })

        // 启动悬浮球
        val btnStart = findViewById<Button>(R.id.btnStartFloat)
        btnStart.text = "🚀 启动悬浮球"
        btnStart.isEnabled = true
        btnStart.setOnClickListener {
            startService(Intent(this, FloatBallService::class.java))
            finish()
        }

        updateStatus()
    }

    private fun updateStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasIme = ImeSwitcher.isMzImeEnabled(this)
        val hasPermission = ImeSwitcher.hasSecurePermission(this)

        tvImeStatus.text = "Mz 输入法: ${if (hasIme) "✅ 已启用" else "❌ 未启用"}"
        tvImeStatus.setTextColor(if (hasIme) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt())

        tvPermStatus.text = "自动切换权限: ${if (hasPermission) "✅ 已激活" else "❌ 未激活"}"
        tvPermStatus.setTextColor(if (hasPermission) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt())

        if (!hasOverlay) {
            val btn = Button(this).apply {
                text = "⚠ 授予悬浮窗权限"; textSize = 16f; setTextColor(0xFFFF8800.toInt())
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
            val root = findViewById<LinearLayout>(R.id.mainLayout)
            root.addView(btn, 1) // 插入到标题后面
        }

        // 启动按钮总是可用
        findViewById<Button>(R.id.btnStartFloat).isEnabled = true
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
