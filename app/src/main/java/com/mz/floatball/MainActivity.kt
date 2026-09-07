package com.mz.floatball

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
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

        handleShortcutIntent(intent)

        tvImeStatus = TextView(this).apply { textSize = 16f; setPadding(0, 16, 0, 8) }
        tvPermStatus = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, 8) }

        val root = findViewById<LinearLayout>(R.id.mainLayout)
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

        // 显示/隐藏悬浮球开关
        val isRunning = isServiceRunning()
        root.addView(Switch(this).apply {
            text = "显示悬浮球"; textSize = 16f; isChecked = isRunning
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    startService(Intent(this@MainActivity, FloatBallService::class.java))
                    Toast.makeText(this@MainActivity, "悬浮球已显示", Toast.LENGTH_SHORT).show()
                } else {
                    stopService(Intent(this@MainActivity, FloatBallService::class.java))
                    Toast.makeText(this@MainActivity, "悬浮球已隐藏", Toast.LENGTH_SHORT).show()
                }
            }
        })
        root.addView(TextView(this).apply { setPadding(0, 8, 0, 0) })

        // 添加到桌面快捷方式
        root.addView(Button(this).apply {
            text = "📱 添加快捷方式到桌面"
            textSize = 16f
            setOnClickListener { addHomeShortcut() }
        })
        root.addView(TextView(this).apply {
            text = "桌面快捷方式可直接切换输入法，无需打开 App"
            textSize = 12f; setTextColor(0xFF666666.toInt()); setPadding(0, 4, 0, 12)
        })

        // 命令调用方式
        root.addView(TextView(this).apply {
            text = "命令行调用：\nam start -a com.mz.floatball.SWITCH_IME"
            textSize = 12f; setTextColor(0xFF666666.toInt()); setPadding(0, 8, 0, 0)
            typeface = android.graphics.Typeface.MONOSPACE
        })

        root.addView(TextView(this).apply { setPadding(0, 8, 0, 0) })

        // 启动按钮
        val btnStart = findViewById<Button>(R.id.btnStartFloat)
        btnStart.text = "🚀 启动悬浮球"
        btnStart.isEnabled = true
        btnStart.setOnClickListener {
            startService(Intent(this, FloatBallService::class.java))
            Toast.makeText(this, "悬浮球已启动", Toast.LENGTH_SHORT).show()
        }

        updateStatus()
    }

    private fun addHomeShortcut() {
        val sm = getSystemService(ShortcutManager::class.java)
        if (sm.isRequestPinShortcutSupported) {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "com.mz.floatball.SWITCH_IME"
                putExtra("from_shortcut", true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val info = ShortcutInfo.Builder(this, "mz_switch_ime")
                .setShortLabel("Mz切换")
                .setLongLabel("Mz悬浮球 - 切换输入法")
                .setIcon(Icon.createWithResource(this, R.drawable.ic_launcher_foreground))
                .setIntent(intent)
                .build()
            sm.requestPinShortcut(info, null)
            Toast.makeText(this, "✅ 快捷方式已添加到桌面", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ 桌面不支持快捷方式", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == "com.mz.floatball.SWITCH_IME" || intent.getBooleanExtra("from_shortcut", false)) {
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatBallService::class.java.name == service.service.className) return true
        }
        return false
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
            val root = findViewById<LinearLayout>(R.id.mainLayout)
            root.addView(Button(this).apply {
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
