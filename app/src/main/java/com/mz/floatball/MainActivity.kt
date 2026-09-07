package com.mz.floatball

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

        // 隐藏默认按钮
        findViewById<TextView>(R.id.tvStatus).visibility = android.view.View.GONE
        findViewById<Button>(R.id.btnOverlay).visibility = android.view.View.GONE
        findViewById<Button>(R.id.btnAccessibility).visibility = android.view.View.GONE

        root.addView(tvImeStatus)
        root.addView(tvPermStatus)

        // 提示
        root.addView(TextView(this).apply {
            text = "首次使用请到「设置 → 管理键盘」开启 Mz 悬浮球输入法"
            textSize = 13f; setTextColor(0xFF888888.toInt()); setPadding(0, 0, 0, 12)
        })

        // 一键复制激活命令
        val cmd = "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        root.addView(Button(this).apply {
            text = "📋 复制激活命令"
            textSize = 16f
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("cmd", cmd))
                Toast.makeText(this@MainActivity, "✅ 已复制！请粘贴到甲壳虫/ADB 执行", Toast.LENGTH_LONG).show()
            }
        })

        root.addView(TextView(this).apply {
            text = "复制后在「甲壳虫调试助手」的 Shell 中粘贴执行"
            textSize = 12f; setTextColor(0xFF666666.toInt()); setPadding(0, 4, 0, 12)
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

        tvImeStatus.text = "Mz 输入法: ${if (hasIme) "✅ 已启用" else "❌ 未启用（去设置开启）"}"
        tvImeStatus.setTextColor(if (hasIme) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt())

        tvPermStatus.text = "自动切换权限: ${if (hasPermission) "✅ 已激活" else "❌ 未激活（点上方按钮复制命令）"}"
        tvPermStatus.setTextColor(if (hasPermission) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt())

        if (!hasOverlay) {
            val rootLayout = findViewById<LinearLayout>(R.id.mainLayout)
            rootLayout.addView(Button(this).apply {
                text = "⚠ 授予悬浮窗权限"
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }, 2)
        }
    }


    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
