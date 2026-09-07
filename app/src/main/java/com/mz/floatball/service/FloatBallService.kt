package com.mz.floatball.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.mz.floatball.R

class FloatBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private var panelView: View? = null
    private var settingsView: View? = null
    private var panelVisible = false
    private var settingsVisible = false
    private lateinit var wmParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams
    private lateinit var settingsParams: WindowManager.LayoutParams
    private lateinit var config: FloatBallConfig
    private val mainHandler = Handler(Looper.getMainLooper())
    private var snapRunnable: Runnable? = null

    private val IME_COMPONENT = "com.mz.floatball/.service.MzInputMethodService"

    override fun onBind(intent: Intent?): IBinder? = null
    private val imeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isMz = intent.getBooleanExtra("is_mz", ImeSwitcher.isMzActive(context))
            updateBallColor(isMz)
            Toast.makeText(context, if (isMz) "→ Mz 输入法" else "← 已切回", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        config = FloatBallConfig(this)
        createFloatBall()
        // 监听输入法切换广播
        val filter = IntentFilter("com.mz.floatball.IME_CHANGED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(imeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(imeReceiver, filter)
        }
    }

    // ==================== 悬浮球 ====================
    private fun createFloatBall() {
        floatView = TextView(this).apply {
            text = getString(R.string.float_ball_label)
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(18, 18, 18, 18)
            setBackgroundResource(R.drawable.ball_background)
            gravity = Gravity.CENTER
            alpha = config.ballAlpha
            elevation = 6f
        }
        wmParams = WindowManager.LayoutParams(
            108, 108,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 400 }
        panelView = createPanel()
        settingsView = createSettingsView()
        windowManager.addView(floatView, wmParams)
        setupDrag()
    }

    private fun setupDrag() {
        var lastX = 0; var lastY = 0; var initialX = 0; var initialY = 0
        var downTime = 0L; var isDragging = false
        val LP = 500L; val triggered = booleanArrayOf(false)

        floatView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX.toInt(); lastY = event.rawY.toInt()
                    initialX = wmParams.x; initialY = wmParams.y
                    downTime = System.currentTimeMillis(); isDragging = false; triggered[0] = false
                    mainHandler.postDelayed({
                        if (!isDragging && !triggered[0]) {
                            triggered[0] = true
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            toggleSettings()
                        }
                    }, LP); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - lastX; val dy = event.rawY.toInt() - lastY
                    if (dx*dx+dy*dy > 64) { isDragging = true; mainHandler.removeCallbacksAndMessages(null) }
                    if (isDragging) { wmParams.x = initialX+dx; wmParams.y = initialY+dy; windowManager.updateViewLayout(floatView, wmParams) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacksAndMessages(null)
                    val dur = System.currentTimeMillis()-downTime
                    if (isDragging) snapToEdge()
                    else if (!triggered[0] && dur < 200) {
                        // 单击：直接切换输入法（核心功能）
                        switchToMzIme()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> { mainHandler.removeCallbacksAndMessages(null); true }
                else -> false
            }
        }
    }

    private fun switchToMzIme() {
        val switched = ImeSwitcher.toggle(this)
        if (switched) {
            val toMz = ImeSwitcher.isMzActive(this)
            updateBallColor(toMz)
            Toast.makeText(this, if (toMz) "→ Mz 输入法" else "← 已切回", Toast.LENGTH_SHORT).show()
            AppLog.log("IME", "智能切换: toMz=$toMz")
        } else {
            Toast.makeText(this, "切换失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBallColor(isMz: Boolean) {
        floatView?.let {
            it.setBackgroundResource(if (isMz) R.drawable.ball_background_active else R.drawable.ball_background)
        }
    }

    private fun switchBackToPreviousIme() {
        ImeSwitcher.switchBack(this)
    }

    // ==================== 工具方法 ====================
    private fun snapToEdge() {
        snapRunnable?.let { mainHandler.removeCallbacks(it) }
        snapRunnable = Runnable {
            val sw = resources.displayMetrics.widthPixels
            wmParams.x = if (wmParams.x+54 < sw/2) 0 else sw-108
            windowManager.updateViewLayout(floatView, wmParams); snapRunnable = null
        }
        mainHandler.postDelayed(snapRunnable!!, config.snapDelayMs)
    }

    private fun applyBallAlpha(a: Float) {
        config.ballAlpha = a; floatView?.alpha = a
        panelView?.rootView?.alpha = a; settingsView?.rootView?.alpha = a
    }

    private fun makePrimaryBtn(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 11f; setTextColor(0xFFFFFFFF.toInt())
        setBackgroundResource(R.drawable.button_primary_bg); setOnClickListener { action() }; isAllCaps = false
    }
    private fun makeSurfaceBtn(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 11f; setTextColor(0xFFFFFFFF.toInt())
        setBackgroundResource(R.drawable.button_surface_bg); setOnClickListener { action() }; isAllCaps = false
    }
    private fun makeWarnBtn(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 11f; setTextColor(0xFFFF9999.toInt())
        setBackgroundResource(R.drawable.button_warn_bg); setOnClickListener { action() }; isAllCaps = false
    }
    private fun separator(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(0,4,0,4) }
        setBackgroundColor(0x25FFFFFF)
    }

    // ==================== 设置面板 ====================
    private fun createSettingsView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(20,16,20,16)
            setBackgroundResource(R.drawable.panel_background); minimumWidth = 280; alpha = config.ballAlpha
        }
        fun title(t: String) = TextView(this).apply { text = t; setTextColor(0xFF8888AA.toInt()); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setPadding(0,8,0,4) }

        val handle = TextView(this).apply { text = "～～～"; textSize = 14f; setTextColor(0xFF555577.toInt()); gravity = Gravity.CENTER; setBackgroundResource(R.drawable.drag_handle_bg); setPadding(0,4,0,4) }
        root.addView(handle)

        root.addView(title("透明度"))
        val alphaVal = TextView(this).apply { text = "${((config.ballAlpha*100).toInt())}%"; setTextColor(0xFFCCCCFF.toInt()); textSize = 14f; typeface = Typeface.MONOSPACE }
        val alphaBar = SeekBar(this).apply {
            max = 90; progress = ((config.ballAlpha-0.1f)*100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { val a = p/100f+0.1f; applyBallAlpha(a); alphaVal.text = "${((a*100).toInt())}%" }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        root.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL
            addView(alphaBar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); addView(alphaVal, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = 8 }) })

        root.addView(separator())
        root.addView(title("贴边延迟"))
        val snapVal = TextView(this).apply { text = "${config.snapDelayMs}ms"; setTextColor(0xFFCCCCFF.toInt()); textSize = 14f; typeface = Typeface.MONOSPACE }
        val snapBar = SeekBar(this).apply {
            max = 20; progress = (config.snapDelayMs/100).toInt().coerceIn(0,20)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { config.snapDelayMs = (p*100).toLong(); snapVal.text = "${config.snapDelayMs}ms" }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        root.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL
            addView(snapBar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); addView(snapVal, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = 8 }) })

        root.addView(separator())
        root.addView(makeSurfaceBtn("关闭设置") { toggleSettings() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 })

        makeDraggable(root, handle) { settingsParams }
        settingsParams = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        return root
    }

    // ==================== 操作面板（IM 输入法兼容） ====================
    private fun createPanel(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(12,12,12,12)
            setBackgroundResource(R.drawable.panel_background); alpha = config.ballAlpha
        }
        val handle = TextView(this).apply { text = "～～～"; textSize = 13f; setTextColor(0xFF555577.toInt()); gravity = Gravity.CENTER; setBackgroundResource(R.drawable.drag_handle_bg); setPadding(0,4,0,8) }
        container.addView(handle)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }

        // 单击悬浮球 = 切换输入法（Mz 输入法直接操作文本）
        row1.addView(makePrimaryBtn("📋复制") { performOnTarget(AccessibilityNodeInfo.ACTION_COPY) })
        row1.addView(makePrimaryBtn("📋粘贴") { performOnTarget(AccessibilityNodeInfo.ACTION_PASTE) })
        row1.addView(makeWarnBtn("✂剪切") { performOnTarget(AccessibilityNodeInfo.ACTION_CUT) })
        row1.addView(makeSurfaceBtn("ALL") { selectAllTarget() })

        row2.addView(makeSurfaceBtn("↩撤销") { undoAction() })
        row2.addView(makeSurfaceBtn("↪重做") { redoAction() })
        row2.addView(makeSurfaceBtn("⌨切换") { switchToMzIme() })
        row2.addView(makeSurfaceBtn("📋") { showLogWindow() })
        row2.addView(makeSurfaceBtn("⚙") { toggleSettings() })

        val btnLP = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = 4; marginEnd = 4; bottomMargin = 4 }
        container.addView(row1, btnLP); container.addView(separator())
        container.addView(row2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 4 })

        val closeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0,4,0,0) }
        closeRow.addView(makeWarnBtn("✕ 关闭") { removePanel() })
        container.addView(closeRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        makeDraggable(container, handle) { panelParams }
        panelParams = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 200 }
        return container
    }

    // ==================== 通用拖动 ====================
    private fun makeDraggable(rootView: View, handleView: View, paramsProvider: () -> WindowManager.LayoutParams) {
        var lx=0; var ly=0; var ix=0; var iy=0; var dragging=false
        handleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lx=event.rawX.toInt(); ly=event.rawY.toInt(); val p=paramsProvider(); ix=p.x; iy=p.y; dragging=false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx=event.rawX.toInt()-lx; val dy=event.rawY.toInt()-ly
                    if(dx*dx+dy*dy>36) dragging=true
                    if(dragging){ val p=paramsProvider(); p.x=ix+dx; p.y=iy+dy; windowManager.updateViewLayout(rootView,p) }
                    true
                }
                MotionEvent.ACTION_UP -> dragging; else -> false
            }
        }
    }

    // ==================== 功能方法 ====================
    private fun toggleSettings() { removePanel(); if (settingsVisible) { settingsView?.let { windowManager.removeView(it) }; settingsVisible=false } else { if (settingsView?.parent==null) windowManager.addView(settingsView, settingsParams); settingsVisible=true } }
    private fun togglePanel() { if (panelVisible) removePanel() else openPanel() }
    private fun openPanel() { if (panelView?.parent==null) windowManager.addView(panelView, panelParams); panelVisible=true }
    private fun removePanel() { if (panelView?.parent!=null) windowManager.removeView(panelView); panelVisible=false }
    private fun performOnTarget(action: Int) {
        val s=ClipboardAccessibilityService.instance
        if (s==null) { Toast.makeText(this,"请先开启无障碍服务",Toast.LENGTH_SHORT).show(); return }
        val ok=s.performTextAction(action)
        if (!ok) Toast.makeText(this,"操作失败",Toast.LENGTH_SHORT).show()
        else { removePanel(); Toast.makeText(this, when(action){ AccessibilityNodeInfo.ACTION_COPY->"✓ 已复制"; AccessibilityNodeInfo.ACTION_PASTE->"✓ 已粘贴"; AccessibilityNodeInfo.ACTION_CUT->"✓ 已剪切"; else->"✓ 完成" }, Toast.LENGTH_SHORT).show() }
    }
    private fun selectAllTarget() { val s=ClipboardAccessibilityService.instance; if(s==null){Toast.makeText(this,"请先开启无障碍服务",Toast.LENGTH_SHORT).show();return}; if(!s.selectAll())Toast.makeText(this,"全选失败",Toast.LENGTH_SHORT).show() else Toast.makeText(this,"✓ 已全选",Toast.LENGTH_SHORT).show() }
    private fun undoAction() { val s=ClipboardAccessibilityService.instance; if(s==null){Toast.makeText(this,"请先开启无障碍服务",Toast.LENGTH_SHORT).show();return}; if(s.undo())Toast.makeText(this,"✓ 已撤销",Toast.LENGTH_SHORT).show() else Toast.makeText(this,"无可撤销",Toast.LENGTH_SHORT).show() }
    private fun redoAction() { val s=ClipboardAccessibilityService.instance; if(s==null){Toast.makeText(this,"请先开启无障碍服务",Toast.LENGTH_SHORT).show();return}; if(s.redo())Toast.makeText(this,"✓ 已重做",Toast.LENGTH_SHORT).show() else Toast.makeText(this,"无可重做",Toast.LENGTH_SHORT).show() }
    private fun showLogWindow() { if(LogWindowService.instance==null)startService(Intent(this,LogWindowService::class.java)); LogWindowService.instance?.showLog() }

    override fun onDestroy() {
        try { unregisterReceiver(imeReceiver) } catch (_: Exception) {}
        snapRunnable?.let{mainHandler.removeCallbacks(it)}
        floatView?.let{windowManager.removeView(it)}
        removePanel()
        if(settingsVisible)settingsView?.let{windowManager.removeView(it)}
        super.onDestroy()
    }
}
