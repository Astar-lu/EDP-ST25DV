package com.epd.st25dv16kc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 共享NFC发送器：100字节小块，鸿蒙兼容
 * 所有Activity共用，改NFC逻辑只改这里
 */
class NfcSender(
    private val activity: androidx.appcompat.app.AppCompatActivity,
    private val onLog: (String) -> Unit
) {
    var nfcAdapter: NfcAdapter? = null
    @Volatile var currentTag: Tag? = null
    @Volatile var isSending = false
        private set

    companion object {
        const val CHUNK_SIZE = 100
        const val FRAME_TOTAL_BYTE = 10000
        const val TOTAL_CHUNK = FRAME_TOTAL_BYTE / CHUNK_SIZE
        const val CMD_PUT_MESSAGE = 0xAA
        const val IC_MFG_CODE = 0x02
        const val CMD_GET_SYS_INFO = 0x2B
        private const val TAG = "ST25DV"
    }

    init {
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
    }

    fun isNfcReady(): Boolean = nfcAdapter?.isEnabled == true

    fun openNfcSettings() {
        activity.startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
    }

    // ===== 生命周期 =====
    fun onResume() {
        if (isSending) return
        try {
            val adapter = nfcAdapter ?: return
            if (!adapter.isEnabled) return
            val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getActivity(activity, 0, intent, flags)
            adapter.enableForegroundDispatch(activity, pi, null, arrayOf(arrayOf("android.nfc.tech.NfcV")))
        } catch (_: Exception) {}
    }

    fun onPause() {
        if (isSending) return
        try { nfcAdapter?.disableForegroundDispatch(activity) } catch (_: Exception) {}
    }

    fun onNewIntent(intent: Intent?): Boolean {
        intent ?: return false
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return false
        currentTag = tag
        return true
    }

    // ===== 连接 =====
    private suspend fun connectStable(): NfcV? {
        val tag = currentTag ?: return null
        for (attempt in 0 until 2) {
            var nfcV: NfcV? = null
            var success = false
            try {
                nfcV = NfcV.get(tag)
                nfcV.connect()
                delay(400)
                val resp = nfcV.transceive(byteArrayOf(0x02, CMD_GET_SYS_INFO.toByte()))
                if (resp.isNotEmpty()) { success = true; return nfcV }
            } catch (e: Exception) {
                if ((e.message ?: "").contains("NFC service died", true)) {
                    onLog("NFC服务崩溃，等8秒...")
                    delay(8000)
                }
            } finally {
                if (!success) try { nfcV?.close() } catch (_: Exception) {}
            }
            delay(500)
        }
        return null
    }

    // ===== 单条发送 =====
    private fun sendPut(nfcV: NfcV, data: ByteArray): Int {
        val msgLen = (data.size - 1).toByte()
        for (flag in intArrayOf(0x02, 0x00)) {
            val cmd = byteArrayOf(flag.toByte(), CMD_PUT_MESSAGE.toByte(), IC_MFG_CODE.toByte(), msgLen) + data
            val resp = try { nfcV.transceive(cmd) } catch (e: Exception) { return -1 }
            if (resp.isEmpty()) continue
            if ((resp[0].toInt() and 0x01) == 0) return 1
            Log.d(TAG, "put f=$flag code=${if (resp.size >= 2) resp[1].toInt() and 0xFF else -1}")
        }
        return 0
    }

    // ===== 整帧发送 =====
    fun sendBitmap(fullFrame: ByteArray, onDone: (Boolean) -> Unit) {
        if (isSending) return
        isSending = true
        onLog("开始发送(${CHUNK_SIZE}B/段,共${TOTAL_CHUNK}段)...")

        CoroutineScope(Dispatchers.IO).launch {
            var nfcV: NfcV? = null
            var success = false
            try {
                try { nfcAdapter?.disableForegroundDispatch(activity) } catch (_: Exception) {}

                nfcV = connectStable()
                if (nfcV == null) { onLog("❌无法连接"); return@launch }
                onLog("连接成功 maxLen=" + nfcV.maxTransceiveLength)

                if (sendPut(nfcV, byteArrayOf(0, 0, 0)) != 1) { onLog("❌Header失败"); return@launch }
                onLog("Header已发送，等初始化...")
                delay(1500)

                for (i in 0 until TOTAL_CHUNK) {
                    val chunk = fullFrame.copyOfRange(i * CHUNK_SIZE, i * CHUNK_SIZE + CHUNK_SIZE)
                    var chunkOk = false
                    for (retry in 0 until 5) {
                        var conn = nfcV
                        if (conn == null || !conn.isConnected) {
                            onLog("[段${i+1}] 重连 retry=$retry")
                            try { nfcV?.close() } catch (_: Exception) {}
                            delay(2000)
                            nfcV = connectStable()
                            conn = nfcV
                            if (conn == null) continue
                        }
                        val result = sendPut(conn, chunk)
                        if (result == 1) { chunkOk = true; break }
                        else if (result == -1) {
                            onLog("[段${i+1}] 断连 retry=$retry")
                            try { conn.close() } catch (_: Exception) {}
                            nfcV = null; delay(2000)
                        } else { delay(400) }
                    }
                    if (!chunkOk) { onLog("❌第${i+1}段失败"); return@launch }
                    if (i % 20 == 19 || i < 3) onLog("已发送 ${i+1}/$TOTAL_CHUNK")
                    delay(if (i < 3) 200L else 80L)
                }
                success = true
                onLog("✅全部发送完成！")
            } catch (e: Exception) {
                onLog("❌异常: " + e.message)
            } finally {
                try { nfcV?.close() } catch (_: Exception) {}
                try {
                    if (!activity.isFinishing) {
                        val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        else PendingIntent.FLAG_UPDATE_CURRENT
                        val pi = PendingIntent.getActivity(activity, 0, intent, flags)
                        nfcAdapter?.enableForegroundDispatch(activity, pi, null, arrayOf(arrayOf("android.nfc.tech.NfcV")))
                    }
                } catch (_: Exception) {}
                isSending = false
                onDone(success)
            }
        }
    }
}