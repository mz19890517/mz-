package com.mz.floatball.service

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLog {
    private const val MAX_LINES = 100
    private val lines = CopyOnWriteArrayList<String>()
    var listener: ((String) -> Unit)? = null

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, msg: String) {
        val line = "[${fmt.format(Date())}] [$tag] $msg"
        lines.add(line)
        if (lines.size > MAX_LINES) lines.removeAt(0)
        listener?.invoke(line)
        android.util.Log.d("MzFloatBall", "$tag: $msg")
    }

    fun dump(): String = lines.joinToString("\n")
}
