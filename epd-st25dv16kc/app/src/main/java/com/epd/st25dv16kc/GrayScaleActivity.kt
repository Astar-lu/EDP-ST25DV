package com.epd.st25dv16kc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.epd.st25dv16kc.databinding.ActivityGrayScaleBinding

class GrayScaleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGrayScaleBinding
    private lateinit var nfcSender: NfcSender
    private var processedBitmap: Bitmap? = null
    private var useColor = true
    private val logBuffer = StringBuilder()
    private lateinit var pickImageLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGrayScaleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvStatus.movementMethod = android.text.method.ScrollingMovementMethod()
        nfcSender = NfcSender(this) { logToUI(it) }

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                try {
                    val src = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                    val bmp200 = Bitmap.createScaledBitmap(src, 200, 200, true)
                    src.recycle()
                    processedBitmap = floydSteinberg(bmp200, useColor)
                    binding.ivPreview.setImageBitmap(processedBitmap)
                    logToUI("模拟灰度完成(${if (useColor) "四色" else "黑白"})")
                } catch (e: Exception) { Toast.makeText(this, "失败: " + e.message, Toast.LENGTH_SHORT).show() }
            }
        }

        binding.toggleMode.setOnCheckedChangeListener { _, isChecked ->
            useColor = isChecked
            processedBitmap?.let {
                processedBitmap = floydSteinberg(it, useColor)
                binding.ivPreview.setImageBitmap(processedBitmap)
            }
        }

        binding.btnSelectImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnNfcSend.setOnClickListener {
            val bmp = processedBitmap
            if (bmp == null) { Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (!nfcSender.isNfcReady()) { nfcSender.openNfcSettings(); return@setOnClickListener }
            logBuffer.setLength(0)
            if (nfcSender.currentTag != null && !nfcSender.isSending) {
                nfcSender.sendBitmap(bitmapToBuffer(bmp, useColor)) {}
            } else { logToUI("请贴近NFC标签") }
        }
    }

    private fun logToUI(msg: String) {
        logBuffer.append(msg).append("\n")
        val lines = logBuffer.toString().split("\n")
        runOnUiThread { binding.tvStatus.text = if (lines.size > 40) lines.takeLast(40).joinToString("\n") else logBuffer.toString() }
    }

    private fun floydSteinberg(src: Bitmap, color: Boolean): Bitmap {
        val w = 200; val h = 200
        val gray = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val c = src.getPixel(x, y)
            gray[y * w + x] = (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val old = gray[i]
            val new = if (color) {
                when { old > 220 -> 255f; old > 160 -> 200f; old > 80 -> 100f; else -> 0f }
            } else { if (old > 128) 255f else 0f }
            val err = old - new
            if (x + 1 < w) gray[i + 1] += err * 7 / 16
            if (y + 1 < h) {
                if (x > 0) gray[i + w - 1] += err * 3 / 16
                gray[i + w] += err * 5 / 16
                if (x + 1 < w) gray[i + w + 1] += err * 1 / 16
            }
            val pixelColor = if (color) {
                when { new > 240 -> Color.WHITE; new > 140 -> Color.YELLOW; new > 50 -> Color.RED; else -> Color.BLACK }
            } else { if (new > 128) Color.WHITE else Color.BLACK }
            out.setPixel(x, y, pixelColor)
        }
        return out
    }

    private fun bitmapToBuffer(bmp: Bitmap, color: Boolean): ByteArray {
        val flip = Matrix().apply { preScale(-1f, 1f) }
        val f = Bitmap.createBitmap(bmp, 0, 0, 200, 200, flip, true)
        val buf = ByteArray(NfcSender.FRAME_TOTAL_BYTE)
        var idx = 0; var bit = 6
        for (y in 0 until 200) for (x in 0 until 200) {
            val px = f.getPixel(x, y)
            val code = if (color) {
                when (px) { Color.BLACK -> 0; Color.WHITE -> 1; Color.YELLOW -> 2; Color.RED -> 3; else -> 0 }
            } else { if (px == Color.BLACK) 0 else 1 }
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
                nfcSender.sendBitmap(bitmapToBuffer(bmp, useColor)) {}
            }
        }
    }
}
