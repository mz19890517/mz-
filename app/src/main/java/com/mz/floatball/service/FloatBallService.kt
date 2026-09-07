package com.mz.floatball.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mz.floatball.R

class FloatBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private var panelView: View? = null
    private var panelVisible = false
    private lateinit var wmParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatBall()
    }

    private fun createFloatBall() {
        floatView = TextView(this).apply {
            text = getString(R.string.float_ball_label)
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 24, 24, 24)
            setBackgroundResource(R.drawable.ball_background)
            gravity = Gravity.CENTER
        }

        wmParams = WindowManager.LayoutParams(
            120, 120,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 400
        }

        panelView = createPanel()

        windowManager.addView(floatView, wmParams)
        setupDrag()

        floatView?.setOnClickListener {
            if (panelVisible) removePanel() else showPanel()
        }
    }

    private fun setupDrag() {
        var lastX = 0
        var lastY = 0
        var initialX = 0
        var initialY = 0
        var downTime = 0L
        var isDragging = false

        floatView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                    initialX = wmParams.x
                    initialY = wmParams.y
                    downTime = System.currentTimeMillis()
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - lastX
                    val dy = event.rawY.toInt() - lastY
                    if (dx * dx + dy * dy > 100) isDragging = true
                    wmParams.x = initialX + dx
                    wmParams.y = initialY + dy
                    windowManager.updateViewLayout(floatView, wmParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - downTime
                    if (!isDragging && duration < 200) {
                        floatView?.performClick()
                    }
                    snapToEdge()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        wmParams.x = if (wmParams.x + 60 < screenWidth / 2) 0 else screenWidth - 120
        windowManager.updateViewLayout(floatView, wmParams)
    }

    private fun createPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            setBackgroundResource(R.drawable.panel_background)
        }

        val buttons = listOf(
            getString(R.string.panel_copy) to { runTextAction(AccessibilityNodeInfo.ACTION_COPY) },
            getString(R.string.panel_paste) to { runTextAction(AccessibilityNodeInfo.ACTION_PASTE) },
            getString(R.string.panel_cut) to { runTextAction(AccessibilityNodeInfo.ACTION_CUT) },
            getString(R.string.panel_select_all) to { selectAllAction() },
            getString(R.string.panel_ocr) to {
                Toast.makeText(this, "OCR 功能开发中...", Toast.LENGTH_SHORT).show()
            },
            getString(R.string.panel_close) to { removePanel() }
        )

        buttons.forEach { (label, action) ->
            val btn = Button(this).apply {
                text = label
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundResource(R.drawable.panel_button_bg)
                setOnClickListener { action() }
            }
            panel.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 4; marginEnd = 4 })
        }

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200
        }

        return panel
    }


    private fun selectAllAction() {
        val service = ClipboardAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }
        val success = service.selectAll()
        if (!success) {
            Toast.makeText(this, "未找到可编辑的输入框", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runTextAction(action: Int) {
        val service = ClipboardAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }
        val success = service.performTextAction(action)
        if (!success) {
            Toast.makeText(this, "未找到可编辑的输入框", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPanel() {
        if (panelView?.parent == null) {
            windowManager.addView(panelView, panelParams)
        }
        panelVisible = true
    }

    private fun removePanel() {
        if (panelView?.parent != null) {
            windowManager.removeView(panelView)
        }
        panelVisible = false
    }

    override fun onDestroy() {
        floatView?.let { windowManager.removeView(it) }
        removePanel()
        super.onDestroy()
    }
}
