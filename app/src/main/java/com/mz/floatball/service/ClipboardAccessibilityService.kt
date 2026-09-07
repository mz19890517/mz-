package com.mz.floatball.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Stack

class ClipboardAccessibilityService : AccessibilityService() {

    private var currentPackageName: String = ""
    private var lastTargetNode: AccessibilityNodeInfo? = null
    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()
    private val MAX_HISTORY = 50
    private val mainHandler = Handler(Looper.getMainLooper())

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.getIntExtra(EXTRA_ACTION, -1)
            if (action != -1) performTextAction(action)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val filter = IntentFilter(ACTION_TEXT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> currentPackageName = it.packageName?.toString() ?: ""
            }
        }
    }
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; unregisterReceiver(actionReceiver); super.onDestroy() }

    // ==================== 节点查找 ====================
    private fun findTargetEditableNode(): AccessibilityNodeInfo? {
        AppLog.log("FindNode", "begin pkg=$currentPackageName windows=${windows.size}")

        // 1. 先用 findFocus 在当前活动窗口找（最可靠）
        val root = rootInActiveWindow
        root?.let { r ->
            val focused = r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && (focused.isEditable || focused.className?.toString()?.contains("EditText") == true)) {
                AppLog.log("FindNode", "findFocus 直接找到: pkg=${focused.packageName}, cls=${focused.className}")
                lastTargetNode = focused; return focused
            }
        }

        // 2. 遍历所有窗口的节点树
        for (window in windows) {
            val wRoot = window.root ?: continue
            val pkg = wRoot.packageName?.toString() ?: continue
            if (pkg == packageName || pkg.contains("inputmethod") || pkg.contains("ime")) continue
            AppLog.log("FindNode", "遍历窗口 pkg=$pkg")
            val found = findFirstEditable(wRoot)
            if (found != null) {
                AppLog.log("FindNode", "找到编辑框: cls=${found.className}")
                lastTargetNode = found; return found
            }
        }
        AppLog.log("FindNode", "未找到编辑框")
        return null
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable || node.className?.toString()?.contains("EditText") == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child) ?: continue
            return found
        }
        return null
    }

    // ==================== 核心操作 ====================
    fun performTextAction(action: Int): Boolean {
        AppLog.log("Action", "action=$action pkg=$currentPackageName")
        val target = findTargetEditableNode() ?: run {
            AppLog.log("Action", "未找到节点，fallback 到手势方案")
            return performGestureAction(action)
        }

        val currentText = target.text?.toString() ?: ""

        // COPY/CUT 前确保有选中文本
        if ((action == AccessibilityNodeInfo.ACTION_COPY || action == AccessibilityNodeInfo.ACTION_CUT) &&
            hasNoSelection(target)) {
            selectAllInNode(target)
        }

        // 先聚焦
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // 粘贴：直接用 SET_TEXT（最可靠）
        var result = if (action == AccessibilityNodeInfo.ACTION_PASTE) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (text != null) {
                AppLog.log("Diag", "剪贴板内容: ${text.take(40)}")
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (!ok) {
                    AppLog.log("Diag", "SET_TEXT 失败，尝试手势粘贴")
                    performGesturePaste(target)
                } else ok
            } else {
                AppLog.log("Diag", "剪贴板为空"); false
            }
        } else {
            val ok = target.performAction(action)
            if (!ok && (action == AccessibilityNodeInfo.ACTION_COPY || action == AccessibilityNodeInfo.ACTION_CUT)) {
                AppLog.log("Diag", "performAction 失败，尝试手势复制/剪切")
                performGestureCopyOrCut(action == AccessibilityNodeInfo.ACTION_CUT)
            } else ok
        }

        if (result) {
            if (undoStack.size >= MAX_HISTORY) undoStack.removeAt(0)
            undoStack.push(currentText); redoStack.clear()
            AppLog.log("Action", "成功, 文本长度=${target.text?.length}")
        } else AppLog.log("Action", "失败")

        return result
    }

    fun selectAll(): Boolean {
        val target = findTargetEditableNode() ?: return false
        return selectAllInNode(target)
    }

    private fun selectAllInNode(node: AccessibilityNodeInfo): Boolean {
        val len = node.text?.length ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply { putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0); putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len) })
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val target = findTargetEditableNode() ?: return false
        val cur = target.text?.toString() ?: ""
        val prev = undoStack.pop(); redoStack.push(cur)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, prev) }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        AppLog.log("Undo", "result=$ok"); return ok
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val target = findTargetEditableNode() ?: return false
        val cur = target.text?.toString() ?: ""
        val next = redoStack.pop(); undoStack.push(cur)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next) }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        AppLog.log("Redo", "result=$ok"); return ok
    }

    // ==================== 手势方案（微信等受限app兜底） ====================

    /** 手势模拟粘贴：在节点位置注入粘贴手势 */
    private fun performGesturePaste(target: AccessibilityNodeInfo): Boolean {
        val rect = android.graphics.Rect()
        target.getBoundsInScreen(rect)
        val cx = rect.centerX().toFloat()
        val cy = rect.centerY().toFloat()
        AppLog.log("Gesture", "粘贴手势: ($cx,$cy)")

        // 长按 → 全选 → 粘贴
        val path1 = Path().apply { moveTo(cx, cy) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, 1000) // 长按 1 秒
        val gesture = GestureDescription.Builder().addStroke(stroke1).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                // 长按后用剪贴板粘贴（通过系统粘贴键）
                performGlobalAction(GLOBAL_ACTION_BACK) // 关闭长按菜单
                mainHandler.postDelayed({
                    // 粘贴
                    val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    val txt = clip?.getItemAt(0)?.coerceToText(this@ClipboardAccessibilityService)?.toString() ?: return@postDelayed
                    val t2 = findTargetEditableNode() ?: return@postDelayed
                    val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, txt) }
                    t2.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }, 300)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {}
        }, null)
    }

    /** 手势模拟复制/剪切：长按 → 全选 → 复制/剪切 */
    private fun performGestureCopyOrCut(isCut: Boolean): Boolean {
        val target = findTargetEditableNode() ?: return false
        val rect = android.graphics.Rect()
        target.getBoundsInScreen(rect)
        val cx = rect.centerX().toFloat()
        val cy = rect.centerY().toFloat()
        AppLog.log("Gesture", "copy/cut 手势: ($cx,$cy) cut=$isCut")

        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1000) // 长按 1 秒
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                // 长按后应该弹出"全选/复制/剪切"菜单，这里用全局操作
                performGlobalAction(GLOBAL_ACTION_BACK) // 尝试关闭菜单
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {}
        }, null)
    }

    /** 手势全局操作兜底 */
    private fun performGestureAction(action: Int): Boolean {
        AppLog.log("Gesture", "全局操作 action=$action")
        return when (action) {
            AccessibilityNodeInfo.ACTION_COPY -> performGestureCopyOrCut(false)
            AccessibilityNodeInfo.ACTION_CUT -> performGestureCopyOrCut(true)
            AccessibilityNodeInfo.ACTION_PASTE -> {
                val target = findTargetEditableNode()
                if (target != null) performGesturePaste(target)
                else performGlobalAction(GLOBAL_ACTION_BACK) // 什么也做不了
            }
            AccessibilityNodeInfo.ACTION_SET_SELECTION -> {
                val t = findTargetEditableNode()
                if (t != null) selectAllInNode(t) else false
            }
            else -> false
        }
    }

    private fun hasNoSelection(node: AccessibilityNodeInfo): Boolean {
        val s = node.textSelectionStart; val e = node.textSelectionEnd
        return s < 0 || e < 0 || s == e
    }

    fun getFocusedText(): String? = findTargetEditableNode()?.text?.toString()
    fun setText(text: String): Boolean {
        val t = findTargetEditableNode() ?: return false
        return t.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
    }

    companion object {
        const val ACTION_TEXT = "com.mz.floatball.ACTION_TEXT"
        const val EXTRA_ACTION = "action"
        var instance: ClipboardAccessibilityService? = null; private set
    }
}
