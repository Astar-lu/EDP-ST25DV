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
import com.epd.st25dv16kc.databinding.ActivityCartoonBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class CartoonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartoonBinding
    private var nfcAdapter: NfcAdapter? = null
    private var tag: Tag? = null
    private var processedBitmap: Bitmap? = null
    private var waitingForSend = false
    private val logBuffer = StringBuilder()
    private var edgeThreshold = 30  // 描边强度，值越小描边越多

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
        binding = ActivityCartoonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvStatus.movementMethod = android.text.method.ScrollingMovementMethod()

        // 描边强度调节
        binding.seekEdge.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                edgeThreshold = progress.coerceAtLeast(5)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                logToUI("描边强度: $edgeThreshold")
            }
        })

        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                try {
                    val srcBmp = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                    val bmp200 = Bitmap.createScaledBitmap(srcBmp, 200, 200, true)
                    srcBmp.recycle()
                    processedBitmap = cartoonize(bmp200)
                    binding.ivPreview.setImageBitmap(processedBitmap)
                    logToUI("卡通化完成（描边强度=$edgeThreshold）")
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
     * 卡通风格：3x3平均平滑降噪 → 边缘检测描黑边 → 四色量化
     */
    private fun cartoonize(src: Bitmap): Bitmap {
        val w = 200
        val h = 200

        // 第一步：3x3 平均平滑，减少噪点，让色块更干净
        val smoothed = Array(h) { IntArray(w) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var count = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val ny = y + dy
                        val nx = x + dx
                        if (ny in 0 until h && nx in 0 until w) {
                            val c = src.getPixel(nx, ny)
                            r += Color.red(c)
                            g += Color.green(c)
                            b += Color.blue(c)
                            count++
                        }
                    }
                }
                smoothed[y][x] = Color.rgb(r / count, g / count, b / count)
            }
        }

        // 预计算灰度
        val gray = Array(h) { IntArray(w) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = smoothed[y][x]
                gray[y][x] = (Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114).toInt()
            }
        }

        // 第二步：边缘检测（与右、下邻居灰度差）+ 四色量化
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val gv = gray[y][x]
                var isEdge = false

                // 与右侧像素对比
                if (x + 1 < w && abs(gv - gray[y][x + 1]) > edgeThreshold) isEdge = true
                // 与下侧像素对比
                if (!isEdge && y + 1 < h && abs(gv - gray[y + 1][x]) > edgeThreshold) isEdge = true
                // 与右下对角对比
                if (!isEdge && x + 1 < w && y + 1 < h && abs(gv - gray[y + 1][x + 1]) > edgeThreshold) isEdge = true

                if (isEdge) {
                    out.setPixel(x, y, Color.BLACK)  // 描边
                } else {
                    // 非边缘区域量化到四色
                    val newColor = when {
                        gv > 220 -> Color.WHITE
                        gv > 160 -> Color.YELLOW
                        gv > 80 -> Color.RED
                        else -> Color.BLACK
                    }
                    out.setPixel(x, y, newColor)
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
                runOnUiThread { Toast.makeText(this@CartoonActivity, "发送完毕", Toast.LENGTH_SHORT).show() }
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