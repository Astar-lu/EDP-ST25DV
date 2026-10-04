package com.epd.st25dv16kc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.epd.st25dv16kc.databinding.ActivityNoteBinding

class NoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNoteBinding
    private lateinit var nfcSender: NfcSender
    private var processedBitmap: Bitmap? = null
    private val logBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvStatus.movementMethod = android.text.method.ScrollingMovementMethod()
        nfcSender = NfcSender(this) { logToUI(it) }

        binding.btnGenerate.setOnClickListener {
            val text = binding.etInput.text.toString()
            if (text.isEmpty()) { Toast.makeText(this, "请输入文字", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            processedBitmap = renderText(text)
            binding.ivPreview.setImageBitmap(processedBitmap)
            logToUI("记事本已生成")
        }

        binding.btnNfcSend.setOnClickListener {
            val bmp = processedBitmap
            if (bmp == null) { Toast.makeText(this, "请先生成", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
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

    private fun renderText(text: String): Bitmap {
        val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK; textSize = 16f; isAntiAlias = false
        }
        val lines = text.split("\n")
        var y = 20f
        for (line in lines) {
            if (y > 190) break
            canvas.drawText(line, 5f, y, paint)
            y += 18f
        }
        return bmp
    }

    private fun bitmapToBuffer(bmp: Bitmap): ByteArray {
        val flip = Matrix().apply { preScale(-1f, 1f) }
        val f = Bitmap.createBitmap(bmp, 0, 0, 200, 200, flip, true)
        val buf = ByteArray(NfcSender.FRAME_TOTAL_BYTE)
        var idx = 0; var bit = 6
        for (y in 0 until 200) for (x in 0 until 200) {
            val code = if (f.getPixel(x, y) == Color.BLACK) 0 else 1
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
