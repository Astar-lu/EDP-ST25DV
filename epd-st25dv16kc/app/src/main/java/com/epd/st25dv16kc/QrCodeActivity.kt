package com.epd.st25dv16kc

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.epd.st25dv16kc.databinding.ActivityQrCodeBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

class QrCodeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQrCodeBinding
    private lateinit var nfcSender: NfcSender
    private var processedBitmap: Bitmap? = null
    private val logBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvStatus.movementMethod = android.text.method.ScrollingMovementMethod()
        nfcSender = NfcSender(this) { logToUI(it) }

        binding.btnGenerate.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            if (text.isEmpty()) { Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            try {
                processedBitmap = generateQR(text)
                binding.ivPreview.setImageBitmap(processedBitmap)
                logToUI("二维码已生成")
            } catch (e: Exception) { Toast.makeText(this, "生成失败: " + e.message, Toast.LENGTH_SHORT).show() }
        }

        binding.btnNfcSend.setOnClickListener {
            val bmp = processedBitmap
            if (bmp == null) { Toast.makeText(this, "请先生成二维码", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (!nfcSender.isNfcReady()) { nfcSender.openNfcSettings(); return@setOnClickListener }
            logBuffer.setLength(0)
            if (nfcSender.currentTag != null && !nfcSender.isSending) {
                nfcSender.sendBitmap(bitmapToBuffer(bmp)) {}
            } else { logToUI("请贴近NFC标签") }
        }
    }

    private fun logToUI(msg: String) {
        logBuffer.append(msg).append("\n")
        val lines = logBuffer.toString().split("\n")
        runOnUiThread { binding.tvStatus.text = if (lines.size > 40) lines.takeLast(40).joinToString("\n") else logBuffer.toString() }
    }

    private fun generateQR(text: String): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 200, 200, hints)
        val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        for (y in 0 until 200) for (x in 0 until 200) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        // 水平翻转修复墨水屏镜像
        return bmp
    }

    private fun bitmapToBuffer(bmp: Bitmap): ByteArray {
        val buf = ByteArray(NfcSender.FRAME_TOTAL_BYTE)
        var idx = 0; var bit = 6
        for (y in 0 until 200) for (x in 0 until 200) {
            val code = if (bmp.getPixel(x, y) == Color.BLACK) 0 else 1
            buf[idx] = (buf[idx].toInt() or (code shl bit)).toByte()
            bit -= 2; if (bit < 0) { bit = 6; idx++ }
        }
        return buf
    }

    override fun onResume() { super.onResume(); nfcSender.onResume() }
    override fun onPause() { super.onPause(); nfcSender.onPause() }
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (nfcSender.onNewIntent(intent)) {
            logToUI("检测到标签")
            val bmp = processedBitmap
            if (bmp != null && !nfcSender.isSending && logBuffer.contains("请贴近")) {
                nfcSender.sendBitmap(bitmapToBuffer(bmp)) {}
            }
        }
    }
}