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
import com.epd.st25dv16kc.databinding.ActivityFourGrayBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FourGrayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFourGrayBinding
    private var nfcAdapter: NfcAdapter? = null
    private var tag: Tag? = null
    private var processedBitmap: Bitmap? = null
    private var waitingForSend = false
    private val logBuffer = StringBuilder()

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
        try {
            binding = ActivityFourGrayBinding.inflate(layoutInflater)
            setContentView(binding.root)
            binding.tvStatus.movementMethod = android.text.method.ScrollingMovementMethod()

            pickImageLauncher = registerForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let {
                    try {
                        val srcBmp = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                        val bmp200 = Bitmap.createScaledBitmap(srcBmp, 200, 200, true)
                        srcBmp.recycle()
                        processedBitmap = quantizeTo4Color(bmp200)
                        binding.ivPreview.setImageBitmap(processedBitmap)
                        logToUI("图片已转为200×200四色")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "图片处理失败: " + e.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            nfcAdapter = NfcAdapter.getDefaultAdapter(this)
            if (nfcAdapter == null) {
                logToUI("本机无NFC硬件")
            }

            binding.btnSelectImage.setOnClickListener {
                pickImageLauncher.launch("image/*")
            }

            binding.btnNfcSend.setOnClickListener {
                if (processedBitmap == null) {
                    Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                logBuffer.setLength(0)
                if (tag != null) {
                    sendFrame(processedBitmap!!)
                } else {
                    waitingForSend = true
                    logToUI("请将NFC标签贴近手机背面...")
                    Toast.makeText(this, "请贴近NFC标签", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "初始化错误: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun logToUI(msg: String) {
        Log.d(TAG, msg)
        logBuffer.append(msg).append("\n")
        val lines = logBuffer.toString().split("\n")
        val recent = if (lines.size > 20) lines.takeLast(20).joinToString("\n") else logBuffer.toString()
        runOnUiThread {
            binding.tvStatus.text = recent
        }
    }

    private fun quantizeTo4Color(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        for (y in 0 until 200) {
            for (x in 0 until 200) {
                val c = src.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
                val newColor = when {
                    gray > 220 -> Color.WHITE
                    gray > 160 -> Color.YELLOW
                    gray > 80 -> Color.RED
                    else -> Color.BLACK
                }
                out.setPixel(x, y, newColor)
            }
        }
        return out
    }

    fun bitmapTo4ColorEpaperBuffer(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        require(w == 200 && h == 200) { "图片必须200×200" }
        val buffer = ByteArray(FRAME_TOTAL_BYTE)
        var byteIndex = 0
        var bitPos = 6
        for (y in 0 until h) {
            for (x in 0 until w) {
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
                if (bitPos < 0) {
                    bitPos = 6
                    byteIndex++
                }
            }
        }
        return buffer
    }

    private suspend fun putMessage(nfcV: NfcV, data: ByteArray, label: String): Boolean {
        val msgLen = (data.size - 1).toByte()
        val header = byteArrayOf(NFC_FLAG.toByte(), CMD_PUT_MESSAGE.toByte(), IC_MFG_CODE.toByte(), msgLen)
        val cmd = header + data

        for (retry in 0 until MAX_RETRY) {
            try {
                val resp = nfcV.transceive(cmd)
                if (resp.isEmpty()) {
                    logToUI("[$label] 空响应 retry=$retry")
                    delay(300)
                    continue
                }
                val errorFlag = resp[0].toInt() and 0x01
                if (errorFlag == 0) {
                    return true
                } else {
                    if (retry % 3 == 0) {
                        logToUI("[$label] 等待MCU retry=$retry")
                    }
                    delay(300)
                }
            } catch (e: Exception) {
                logToUI("[$label] 异常: " + e.message + " retry=" + retry)
                delay(300)
            }
        }
        return false
    }

    private fun ByteArray.toHexString(): String {
        val sb = StringBuilder()
        for (b in this) {
            sb.append(String.format("%02X ", b))
        }
        return sb.toString().trim()
    }

    private fun sendFrame(bitmap: Bitmap) {
        val fullFrame = bitmapTo4ColorEpaperBuffer(bitmap)

        logToUI("开始发送，标签保持紧贴...")

        CoroutineScope(Dispatchers.IO).launch {
            val currentTag = tag ?: run {
                logToUI("未检测到NFC标签")
                return@launch
            }

            logToUI("标签UID: " + currentTag.id.toHexString())

            val nfcV = NfcV.get(currentTag)
            try {
                nfcV.connect()
                delay(200)

                val header = byteArrayOf(0x00, 0x00, 0x00)
                if (!putMessage(nfcV, header, "Header")) {
                    logToUI("❌Header发送失败")
                    return@launch
                }
                logToUI("Header已发送，墨水屏初始化...")
                delay(2000)

                for (i in 0 until TOTAL_CHUNK) {
                    val offset = i * CHUNK_SIZE
                    val chunk = fullFrame.copyOfRange(offset, offset + CHUNK_SIZE)

                    if (!nfcV.isConnected) {
                        nfcV.connect()
                        delay(100)
                    }

                    if (!putMessage(nfcV, chunk, "段" + (i + 1))) {
                        logToUI("❌第" + (i + 1) + "段发送失败")
                        return@launch
                    }

                    if (i % 10 == 9) {
                        logToUI("已发送 " + (i + 1) + "/$TOTAL_CHUNK 段")
                    }

                    if (i < 3) {
                        delay(500)
                    } else {
                        delay(200)
                    }
                }

                logToUI("✅全部50段发送完成！墨水屏正在刷新...")
                runOnUiThread {
                    Toast.makeText(this@FourGrayActivity, "发送完毕，等待屏幕刷新", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logToUI("❌NFC异常: " + e.message)
            } finally {
                try {
                    nfcV.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, flags)
            val techFilter = arrayOf(arrayOf("android.nfc.tech.NfcV"))
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, techFilter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        logToUI("检测到NFC标签")
        if (waitingForSend && tag != null) {
            waitingForSend = false
            processedBitmap?.let {
                sendFrame(it)
            }
        }
    }
}