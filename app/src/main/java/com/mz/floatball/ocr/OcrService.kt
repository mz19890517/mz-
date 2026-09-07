package com.mz.floatball.ocr

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import java.util.concurrent.Executors

/**
 * OCR 文字识别服务（预留接口）
 *
 * 后续可接入：
 * - PaddleOCR (离线，中文优秀)
 * - Google ML Kit (离线)
 * - 腾讯云/百度 OCR API (联网，精度最高)
 *
 * 使用示例：
 *   OcrService.recognize(bitmap) { result ->
 *       // result: OcrResult
 *       println("识别文字: ${result.text}")
 *   }
 */
class OcrService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    data class OcrResult(
        val text: String,
        val confidence: Float = 0f,
        val boxes: List<Rect> = emptyList()
    )

    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    companion object {
        private val executor = Executors.newSingleThreadExecutor()

        /**
         * 识别图片中的文字
         * TODO: 替换为真实 OCR 引擎实现
         */
        fun recognize(bitmap: Bitmap, callback: (OcrResult) -> Unit) {
            executor.execute {
                // 模拟 OCR 识别（实际接入后替换此处）
                val result = OcrResult(
                    text = "[OCR 功能待接入]\n请在 build.gradle 中添加 OCR 依赖",
                    confidence = 0f
                )
                callback(result)
            }
        }
    }
}
