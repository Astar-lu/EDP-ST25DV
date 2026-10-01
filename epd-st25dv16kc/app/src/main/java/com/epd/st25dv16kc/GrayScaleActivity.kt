package com.epd.st25dv16kc

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.epd.st25dv16kc.databinding.ActivityGrayScaleBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GrayScaleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGrayScaleBinding
    private var nfcAdapter: NfcAdapter? = null
    private var tag: Tag? = null
    private var processedBitmap: Bitmap? = null
    private var waitingForSend = false
    private val logBuffer = StringBuilder()
    private var useBlackWhite = false  // false=四色抖动, true=黑白抖动

    private lateinit var pickImageLauncher: androidx.activity.result.ActivityResultLauncher<String>

    companion object {
        private const val TOTAL_CHUNK = 50
        private const val CHUNK_SIZE = 200
        private const val FRAME_TOTAL_BYTE = 10000
        private const val NFC_FLAG = 0x02
        private const val CMD_PUT_MESSAGE = 0xAA
        private const val IC_MFG_CODE = 0x02
        private const val MAX_RETRY = 10
        private const val TAG = "ST25DV"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGrayScaleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvStatus.movementMethod = android.text.method.ScrollingMovementMethod()

        // 模式切换
        binding.btnMode.setOnCheckedChangeListener { _, isChecked ->
            useBlackWhite = isChecked
            binding.btnSelectImage.text = if (isChecked) "选择图片并黑白抖动" else "选择图片并抖动转四色"
            processedBitmap = null
            binding.ivPreview.setImageBitmap(null)
            logToUI(if (isChecked) "已切换为黑白抖动模式" else "已切换为四色抖动模式")
        }

        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                try {
                    val srcBmp = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                    val bmp200 = Bitmap.createScaledBitmap(srcBmp, 200, 200, true)
                    srcBmp.recycle()
                    processedBitmap = floydSteinbergDither(bmp200, useBlackWhite)
                    binding.ivPreview.setImageBitmap(processedBitmap)
                    logToUI(if (useBlackWhite) "图片已黑白抖动" else "图片已四色抖动")
                } catch (e: Exception) {
                    Toast.makeText(this, "图片处理失败: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        binding.btnSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnNfcSend.setOnClickListener {
            if (processedBitmap == null) {
                Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            logBuffer.setLength(0)
            if (tag != null) sendFrame(processedBitmap!!)
            else {
                waitingForSend = true
                logToUI("请贴近NFC标签")
            }
        }
    }

    /**
     * Floyd-Steinberg 误差扩散抖动
     * @param blackWhite true=纯黑白两色抖动, false=四色(黑红黄白)抖动
     */
    private fun floydSteinbergDither(src: Bitmap, blackWhite: Boolean): Bitmap {
        val w = 200
        val h = 200
        val gray = Array(h) { FloatArray(w) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = src.getPixel(x, y)
                gray[y][x] = (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // 调色板：黑白模式用 [0,255]，四色模式用 [0,80,200,255]
        val palette: IntArray
        val paletteColor: IntArray
        if (blackWhite) {
            palette = intArrayOf(0, 255)
            paletteColor = intArrayOf(Color.BLACK, Color.WHITE)
        } else {
            palette = intArrayOf(0, 80, 200, 255)
            paletteColor = intArrayOf(Color.BLACK, Color.RED, Color.YELLOW, Color.WHITE)
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val oldPixel = gray[y][x].coerceIn(0f, 255f)
                var bestIdx = 0
                var bestDist = Float.MAX_VALUE
                for (i in palette.indices) {
                    val d = Math.abs(oldPixel - palette[i])
                    if (d < bestDist) { bestDist = d; bestIdx = i }
                }
                out.setPixel(x, y, paletteColor[bestIdx])
                val newPixel = palette[bestIdx].toFloat()
                val error = oldPixel - newPixel
                if (x + 1 < w) gray[y][x + 1] += error * 7f / 16f
                if (y + 1 < h) {
                    if (x - 1 >= 0) gray[y + 1][x - 1] += error * 3f / 16f
                    gray[y + 1][x] += error * 5f / 16f
                    if (x + 1 < w) gray[y + 1][x + 1] += error * 1f / 16f
                }
            }
        }
        return out
    }

    private fun bitmapTo4ColorEpaperBuffer(bmp: Bitmap): ByteArray {
        val buffer = ByteArray(FRAME_TOTAL_BYTE)
        var byteIndex = 0
        var bitPos = 6
        for (y in 0 until 200) {
            for (x in 0 until 200) {
                val px = bmp.getPixel(x, y)
                val pixelCode = when (px) {
                    Color.BLACK -> 0
                    Color.WHITE -> 1
                    Color.YELLOW -> 2
                    Color.RED -> 3
                    else -> 0
                }
                buffer[byteIndex] = (buffer[byteIndex].toInt() or (pixelCode shl bitPos)).toByte()
                bitPos -= 2
                if (bitPos < 0) { bitPos = 6; byteIndex++ }
            }
        }
        return buffer
    }

    private fun logToUI(msg: String) {
        Log.d(TAG, msg)
        logBuffer.append(msg).append("\n")
        val lines = logBuffer.toString().split("\n")
        val recent = if (lines.size > 20) lines.takeLast(20).joinToString("\n") else logBuffer.toString()
        runOnUiThread { binding.tvStatus.text = recent }
    }

    private suspend fun putMessage(nfcV: NfcV, data: ByteArray, label: String): Boolean {
        val msgLen = (data.size - 1).toByte()
        val cmd = byteArrayOf(NFC_FLAG.toByte(), CMD_PUT_MESSAGE.toByte(), IC_MFG_CODE.toByte(), msgLen) + data
        for (retry in 0 until MAX_RETRY) {
            try {
                val resp = nfcV.transceive(cmd)
                if (resp.isEmpty()) { delay(300); continue }
                if ((resp[0].toInt() and 0x01) == 0) return true
                delay(300)
            } catch (e: Exception) {
                if (retry % 3 == 0) logToUI("[$label] 等待MCU retry=$retry")
                delay(300)
            }
        }
        return false
    }

    private fun sendFrame(bitmap: Bitmap) {
        val fullFrame = bitmapTo4ColorEpaperBuffer(bitmap)
        logToUI("开始发送...")
        CoroutineScope(Dispatchers.IO).launch {
            val currentTag = tag ?: run { logToUI("未检测到标签"); return@launch }
            val nfcV = NfcV.get(currentTag)
            try {
                nfcV.connect()
                delay(200)
                if (!putMessage(nfcV, byteArrayOf(0, 0, 0), "Header")) { logToUI("❌Header失败"); return@launch }
                logToUI("Header已发送")
                delay(2000)
                for (i in 0 until TOTAL_CHUNK) {
                    val chunk = fullFrame.copyOfRange(i * CHUNK_SIZE, i * CHUNK_SIZE + CHUNK_SIZE)
                    if (!nfcV.isConnected) { nfcV.connect(); delay(100) }
                    if (!putMessage(nfcV, chunk, "段" + (i + 1))) { logToUI("❌第${i + 1}段失败"); return@launch }
                    if (i % 10 == 9) logToUI("已发送 ${i + 1}/$TOTAL_CHUNK")
                    delay(if (i < 3) 500L else 200L)
                }
                logToUI("✅全部发送完成！")
                runOnUiThread { Toast.makeText(this@GrayScaleActivity, "发送完毕", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                logToUI("❌NFC异常: " + e.message)
            } finally {
                try { nfcV.close() } catch (_: Exception) {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            else android.app.PendingIntent.FLAG_UPDATE_CURRENT
            val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, flags)
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, arrayOf(arrayOf("android.nfc.tech.NfcV")))
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onPause() {
        super.onPause()
        try { nfcAdapter?.disableForegroundDispatch(this) } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (waitingForSend && tag != null) {
            waitingForSend = false
            processedBitmap?.let { sendFrame(it) }
        }
    }
}