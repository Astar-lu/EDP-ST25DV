package com.epd.st25dv16kc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.epd.st25dv16kc.databinding.ActivityCartoonBinding

class CartoonActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCartoonBinding
    private lateinit var nfcSender: NfcSender
    private var processedBitmap: Bitmap? = null
    private var edgeStrength = 30
    private val logBuffer = StringBuilder()
    private lateinit var pickImageLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartoonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvStatus.movementMethod = android.text.method.ScrollingMovementMethod()
        nfcSender = NfcSender(this) { logToUI(it) }

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                try {
                    val src = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                    val bmp200 = Bitmap.createScaledBitmap(src, 200, 200, true)
                    src.recycle()
                    processedBitmap = cartoonize(bmp200, edgeStrength)
                    binding.ivPreview.setImageBitmap(processedBitmap)
                    logToUI("卡通风格完成")
                } catch (e: Exception) { Toast.makeText(this, "失败: " + e.message, Toast.LENGTH_SHORT).show() }
            }
        }

        binding.seekEdge.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, v: Int, f: Boolean) { edgeStrength = v + 10 }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                processedBitmap?.let {
                    // 重新处理需要原图，这里简化：提示重新选图
                    logToUI("描边强度=$edgeStrength，重新选图生效")
                }
            }
        })

        binding.btnSelectImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnNfcSend.setOnClickListener {
            val bmp = processedBitmap
            if (bmp == null) { Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
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

    private fun cartoonize(src: Bitmap, threshold: Int): Bitmap {
        val w = 200; val h = 200
        // 3x3 平均平滑
        val smooth = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0; var g = 0; var b = 0; var cnt = 0
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) {
                    val c = src.getPixel(nx, ny); r += Color.red(c); g += Color.green(c); b += Color.blue(c); cnt++
                }
            }
            smooth.setPixel(x, y, Color.rgb(r / cnt, g / cnt, b / cnt))
        }
        // 边缘检测 + 四色量化
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            val c = smooth.getPixel(x, y)
            val gray = (Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114).toInt()
            var isEdge = false
            if (x + 1 < w && y + 1 < h) {
                val c1 = smooth.getPixel(x + 1, y)
                val c2 = smooth.getPixel(x, y + 1)
                val g1 = (Color.red(c1) * 0.299 + Color.green(c1) * 0.587 + Color.blue(c1) * 0.114).toInt()
                val g2 = (Color.red(c2) * 0.299 + Color.green(c2) * 0.587 + Color.blue(c2) * 0.114).toInt()
                if (Math.abs(gray - g1) > threshold || Math.abs(gray - g2) > threshold) isEdge = true
            }
            val color = if (isEdge) Color.BLACK else when {
                gray > 220 -> Color.WHITE; gray > 160 -> Color.YELLOW; gray > 80 -> Color.RED; else -> Color.BLACK
            }
            out.setPixel(x, y, color)
        }
        return out
    }

    private fun bitmapToBuffer(bmp: Bitmap): ByteArray {
        val buf = ByteArray(NfcSender.FRAME_TOTAL_BYTE)
        var idx = 0; var bit = 6
        for (y in 0 until 200) for (x in 0 until 200) {
            val px = bmp.getPixel(x, y)
            val code = when (px) { Color.BLACK -> 0; Color.WHITE -> 1; Color.YELLOW -> 2; Color.RED -> 3; else -> 0 }
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