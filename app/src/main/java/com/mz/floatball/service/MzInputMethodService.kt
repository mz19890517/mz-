package com.mz.floatball.service

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.TextView
import android.widget.Toast
import com.mz.floatball.R

class MzInputMethodService : InputMethodService() {

    private var ic: InputConnection? = null
    private var currentEditorPkg: String = ""

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.ime_layout, null)
        setupButtons(view)
        return view
    }

    private fun setupButtons(view: View) {
        view.findViewById<TextView>(R.id.ime_btn_selectall)?.setOnClickListener { doSelectAll() }
        view.findViewById<TextView>(R.id.ime_btn_copy)?.setOnClickListener { doCopy() }
        view.findViewById<TextView>(R.id.ime_btn_paste)?.setOnClickListener { doPaste() }
        view.findViewById<TextView>(R.id.ime_btn_cut)?.setOnClickListener { doCut() }
        view.findViewById<TextView>(R.id.ime_btn_undo)?.setOnClickListener { doUndo() }
        view.findViewById<TextView>(R.id.ime_btn_redo)?.setOnClickListener { doRedo() }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        ic = currentInputConnection
        currentEditorPkg = attribute?.packageName ?: ""
        AppLog.log("IME", "onStartInput pkg=$currentEditorPkg")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        ic = currentInputConnection
        currentEditorPkg = info?.packageName ?: ""
        AppLog.log("IME", "onStartInputView pkg=$currentEditorPkg")
    }

    // ==================== 操作方法 ====================
    private fun doSelectAll() {
        val conn = ic ?: run { AppLog.log("IME", "InputConnection is null"); return }
        val ok = conn.performContextMenuAction(android.R.id.selectAll)
        AppLog.log("IME", "selectAll: $ok")
        if (!ok) {
            val text = conn.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
            if (text != null && text.text != null) {
                conn.setSelection(0, text.text.length)
                AppLog.log("IME", "手动全选")
            }
        }
        Toast.makeText(this, "✓ 已全选", Toast.LENGTH_SHORT).show()
    }

    private fun doCopy() {
        val conn = ic ?: return
        val extracted = conn.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        val text = extracted?.text?.toString() ?: ""
        if (text.isNotEmpty()) {
            val selStart = extracted?.selectionStart ?: -1
            val selEnd = extracted?.selectionEnd ?: -1
            val selected = if (selStart != selEnd && selStart >= 0 && selEnd >= 0)
                text.substring(selStart.coerceAtMost(selEnd), selEnd.coerceAtLeast(selStart))
            else text
            val clip = android.content.ClipData.newPlainText("mz_floatball", selected)
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
            AppLog.log("IME", "复制: ${selected.take(40)}")
            Toast.makeText(this, "✓ 已复制", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "无内容可复制", Toast.LENGTH_SHORT).show()
    }

    private fun doPaste() {
        val conn = ic ?: return
        val clip = (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text != null) {
            val ok = conn.commitText(text, 1)
            AppLog.log("IME", "粘贴: ${text.take(40)} committed=$ok")
            Toast.makeText(this, "✓ 已粘贴", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
    }

    private fun doCut() {
        val conn = ic ?: return
        val extracted = conn.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        val text = extracted?.text?.toString() ?: ""
        if (text.isNotEmpty()) {
            val selStart = extracted?.selectionStart ?: -1
            val selEnd = extracted?.selectionEnd ?: -1
            val selected = if (selStart != selEnd && selStart >= 0 && selEnd >= 0)
                text.substring(selStart.coerceAtMost(selEnd), selEnd.coerceAtLeast(selStart))
            else text
            val clip = android.content.ClipData.newPlainText("mz_floatball", selected)
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
            if (selStart >= 0 && selEnd >= 0 && selStart != selEnd)
                conn.setSelection(selStart.coerceAtMost(selEnd), selStart.coerceAtMost(selEnd))
            AppLog.log("IME", "剪切: ${selected.take(40)}")
            Toast.makeText(this, "✓ 已剪切", Toast.LENGTH_SHORT).show()
        }
    }

    private fun doUndo() {
        val conn = ic ?: return
        val ok = conn.performContextMenuAction(android.R.id.undo)
        AppLog.log("IME", "undo: $ok")
        if (ok) Toast.makeText(this, "✓ 已撤销", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "无可撤销", Toast.LENGTH_SHORT).show()
    }

    private fun doRedo() {
        val conn = ic ?: return
        val ok = conn.performContextMenuAction(android.R.id.redo)
        AppLog.log("IME", "redo: $ok")
        if (ok) Toast.makeText(this, "✓ 已重做", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "无可重做", Toast.LENGTH_SHORT).show()
    }
}
