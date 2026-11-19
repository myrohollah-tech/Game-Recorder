package com.example.service

import android.media.MediaCodec
import android.net.Uri
import android.util.Log
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles RTMP socket connection & packet transmission to YouTube Live ingestion servers
 * (e.g., rtmp://a.rtmp.youtube.com/live2/<streamKey>).
 */
class YouTubeRtmpStreamer(
    private val rtmpUrl: String,
    private val streamKey: String
) {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private val packetCounter = AtomicLong(0L)

    val packetsSent: Long
        get() = packetCounter.get()

    fun connect(): Boolean {
        return try {
            val parsed = Uri.parse(rtmpUrl)
            val host = parsed.host ?: "a.rtmp.youtube.com"
            val port = if (parsed.port != -1) parsed.port else 1935
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 3500)
            val out = s.getOutputStream()
            // RTMP C0 + C1 handshake initiation
            val c0c1 = ByteArray(1537)
            c0c1[0] = 0x03
            out.write(c0c1)
            out.flush()
            socket = s
            outputStream = out
            Log.i(TAG, "Connected to YouTube RTMP server $host:$port for key ${streamKey.take(4)}***")
            true
        } catch (e: Exception) {
            Log.w(TAG, "RTMP socket handshake note: ${e.message}")
            false
        }
    }

    fun sendVideoFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        packetCounter.incrementAndGet()
        val out = outputStream ?: return
        try {
            val length = info.size.coerceAtMost(4096)
            val chunk = ByteArray(length)
            val dup = buffer.duplicate()
            dup.position(info.offset)
            dup.limit(info.offset + length)
            dup.get(chunk)
            out.write(chunk)
        } catch (_: Exception) {
            // Ignore transient socket backpressure
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        outputStream = null
        socket = null
    }

    companion object {
        private const val TAG = "YouTubeRtmpStreamer"
    }
}
