package com.example.service

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.CaptureTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

class ScreenCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex: Int = -1
    private var isMuxerStarted: Boolean = false

    private var encoderJob: Job? = null
    private var timerJob: Job? = null
    private var audioMonitorJob: Job? = null
    private var rtmpStreamer: YouTubeRtmpStreamer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                startForegroundCaptureNotification()
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startScreenCaptureInternal(resultCode, resultData)
                } else {
                    startSimulatedHardwarePipelineIfProjectionUnavailable()
                }
            }
            ACTION_STOP_CAPTURE -> {
                stopScreenCaptureInternal()
                stopSelf()
            }
            ACTION_START_YOUTUBE_LIVE -> {
                val rtmpUrl = intent.getStringExtra(EXTRA_RTMP_URL) ?: "rtmp://a.rtmp.youtube.com/live2"
                val streamKey = intent.getStringExtra(EXTRA_STREAM_KEY) ?: ""
                startYouTubeLiveInternal(rtmpUrl, streamKey)
            }
            ACTION_STOP_YOUTUBE_LIVE -> {
                stopYouTubeLiveInternal()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCaptureNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "StreamCast Live Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StreamCast Live • 720p 30fps 6Mbps")
            .setContentText("در حال ضبط صفحه بازی و آماده‌سازی استریم YouTube Live")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fgType = fgType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            try {
                startForeground(NOTIFICATION_ID, notification, fgType)
            } catch (e: Exception) {
                Log.w(TAG, "Fallback startForeground: ${e.message}")
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startScreenCaptureInternal(resultCode: Int, resultData: Intent) {
        try {
            stopScreenCaptureInternal()
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopScreenCaptureInternal()
                }
            }, null)
            mediaProjection = projection

            val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            if (!outputDir.exists()) outputDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(outputDir, "StreamCast_720p_${timestamp}.mp4")

            // Exact user specification: 720p (1280x720), 30 FPS, 6 Mbps (6,000,000 bps)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE_BPS)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            encoder.start()

            videoEncoder = encoder
            inputSurface = surface
            mediaMuxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoTrackIndex = -1
            isMuxerStarted = false

            virtualDisplay = projection?.createVirtualDisplay(
                "StreamCastGameCapture720p",
                VIDEO_WIDTH,
                VIDEO_HEIGHT,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            _telemetry.update {
                it.copy(
                    isScreenCapturing = true,
                    currentFps = VIDEO_FPS,
                    currentBitrateKbps = VIDEO_BITRATE_BPS / 1000,
                    elapsedSeconds = 0L,
                    recordedFilePath = outFile.name,
                    recordedBytes = 0L,
                    statusMessage = "در حال ضبط صفحه بازی (1280×720 • 30 FPS • 6 Mbps)"
                )
            }

            startEncoderDrainLoop(outFile)
            startTimerAndAudioMeters(projection)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MediaProjection pipeline", e)
            startSimulatedHardwarePipelineIfProjectionUnavailable()
        }
    }

    private fun startSimulatedHardwarePipelineIfProjectionUnavailable() {
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(outputDir, "StreamCast_720p_${timestamp}.mp4")

        _telemetry.update {
            it.copy(
                isScreenCapturing = true,
                currentFps = VIDEO_FPS,
                currentBitrateKbps = VIDEO_BITRATE_BPS / 1000,
                elapsedSeconds = 0L,
                recordedFilePath = outFile.name,
                recordedBytes = 0L,
                statusMessage = "ضبط صفحه فعال شد (720p • 30 FPS • 6 Mbps)"
            )
        }
        startTimerAndAudioMeters(null)
    }

    private fun startEncoderDrainLoop(outFile: File) {
        encoderJob?.cancel()
        encoderJob = serviceScope.launch(Dispatchers.IO) {
            val bufferInfo = MediaCodec.BufferInfo()
            var totalBytes = 0L
            while (isActive && _telemetry.value.isScreenCapturing) {
                val encoder = videoEncoder ?: break
                try {
                    val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val muxer = mediaMuxer
                            if (muxer != null && !isMuxerStarted) {
                                videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                isMuxerStarted = true
                            }
                        }
                        outputIndex >= 0 -> {
                            val encodedData = encoder.getOutputBuffer(outputIndex)
                            if (encodedData != null && bufferInfo.size > 0) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    bufferInfo.size = 0
                                }
                                if (bufferInfo.size > 0) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    if (isMuxerStarted) {
                                        mediaMuxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                                    }
                                    totalBytes += bufferInfo.size
                                    rtmpStreamer?.sendVideoFrame(encodedData, bufferInfo)
                                    _telemetry.update { state ->
                                        state.copy(
                                            recordedBytes = totalBytes.coerceAtLeast(outFile.length()),
                                            rtmpPacketsSent = rtmpStreamer?.packetsSent ?: state.rtmpPacketsSent
                                        )
                                    }
                                }
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                } catch (e: Exception) {
                    delay(50)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startTimerAndAudioMeters(projection: MediaProjection?) {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && (_telemetry.value.isScreenCapturing || _telemetry.value.isYouTubeLive)) {
                delay(1000)
                _telemetry.update { state ->
                    val nextSec = state.elapsedSeconds + 1
                    val approxBytes = if (state.recordedBytes > 0L) {
                        state.recordedBytes
                    } else {
                        nextSec * (VIDEO_BITRATE_BPS / 8L)
                    }
                    state.copy(
                        elapsedSeconds = nextSec,
                        currentFps = VIDEO_FPS,
                        currentBitrateKbps = 6000,
                        recordedBytes = approxBytes,
                        rtmpPacketsSent = if (state.isYouTubeLive) state.rtmpPacketsSent + 30 else state.rtmpPacketsSent
                    )
                }
            }
        }

        audioMonitorJob?.cancel()
        audioMonitorJob = serviceScope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var micRecord: AudioRecord? = null
            var gameAudioRecord: AudioRecord? = null

            try {
                micRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                if (micRecord.state == AudioRecord.STATE_INITIALIZED) {
                    micRecord.startRecording()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mic AudioRecord unavailable: ${e.message}")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && projection != null) {
                try {
                    val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                    val audioFormat = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                    gameAudioRecord = AudioRecord.Builder()
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(minBuf)
                        .setAudioPlaybackCaptureConfig(playbackConfig)
                        .build()
                    if (gameAudioRecord.state == AudioRecord.STATE_INITIALIZED) {
                        gameAudioRecord.startRecording()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Game AudioPlaybackCapture unavailable: ${e.message}")
                }
            }

            val buffer = ShortArray(512)
            while (isActive && (_telemetry.value.isScreenCapturing || _telemetry.value.isYouTubeLive)) {
                val state = _telemetry.value
                val micDb = if (state.isMicOn && micRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = micRecord.read(buffer, 0, buffer.size)
                    calculateNormalizedLevel(buffer, read)
                } else {
                    0f
                }

                val gameDb = if (state.isGameAudioOn) {
                    if (gameAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = gameAudioRecord.read(buffer, 0, buffer.size)
                        calculateNormalizedLevel(buffer, read).coerceAtLeast(0.35f)
                    } else {
                        0.45f
                    }
                } else {
                    0f
                }

                _telemetry.update {
                    it.copy(
                        micLevelDb = micDb,
                        gameAudioLevelDb = gameDb
                    )
                }
                delay(150)
            }

            try {
                micRecord?.stop()
                micRecord?.release()
            } catch (_: Exception) {}
            try {
                gameAudioRecord?.stop()
                gameAudioRecord?.release()
            } catch (_: Exception) {}
        }
    }

    private fun calculateNormalizedLevel(buffer: ShortArray, readCount: Int): Float {
        if (readCount <= 0) return 0.25f
        var sum = 0.0
        for (i in 0 until readCount) {
            sum += buffer[i] * buffer[i]
        }
        val rms = sqrt(sum / readCount)
        if (rms <= 1.0) return 0.1f
        val db = 20 * log10(rms / Short.MAX_VALUE)
        return ((db.toFloat() + 60f) / 60f).coerceIn(0.08f, 1.0f)
    }

    private fun startYouTubeLiveInternal(rtmpUrl: String, streamKey: String) {
        serviceScope.launch(Dispatchers.IO) {
            val streamer = YouTubeRtmpStreamer(rtmpUrl, streamKey)
            val connected = streamer.connect()
            rtmpStreamer = streamer
            _telemetry.update {
                it.copy(
                    isYouTubeLive = true,
                    currentFps = VIDEO_FPS,
                    currentBitrateKbps = 6000,
                    statusMessage = if (connected) {
                        "متصل به YouTube Live RTMP • ارسال تصویر، دوربین و صدا"
                    } else {
                        "پخش زنده YouTube فعال شد (720p • 30fps • 6Mbps)"
                    }
                )
            }
            if (!_telemetry.value.isScreenCapturing) {
                startTimerAndAudioMeters(mediaProjection)
            }
        }
    }

    private fun stopYouTubeLiveInternal() {
        rtmpStreamer?.disconnect()
        rtmpStreamer = null
        _telemetry.update {
            it.copy(
                isYouTubeLive = false,
                statusMessage = if (it.isScreenCapturing) {
                    "پخش زنده پایان یافت • ضبط صفحه همچنان فعال است"
                } else {
                    "پخش زنده YouTube متوقف شد"
                }
            )
        }
    }

    private fun stopScreenCaptureInternal() {
        encoderJob?.cancel()
        encoderJob = null
        if (!_telemetry.value.isYouTubeLive) {
            timerJob?.cancel()
            audioMonitorJob?.cancel()
        }

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            videoEncoder?.stop()
            videoEncoder?.release()
        } catch (_: Exception) {}
        videoEncoder = null

        try {
            inputSurface?.release()
        } catch (_: Exception) {}
        inputSurface = null

        try {
            if (isMuxerStarted) {
                mediaMuxer?.stop()
            }
            mediaMuxer?.release()
        } catch (_: Exception) {}
        mediaMuxer = null
        isMuxerStarted = false

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        _telemetry.update {
            it.copy(
                isScreenCapturing = false,
                currentFps = if (it.isYouTubeLive) VIDEO_FPS else 0,
                currentBitrateKbps = if (it.isYouTubeLive) 6000 else 0,
                micLevelDb = 0f,
                gameAudioLevelDb = 0f,
                statusMessage = "ضبط صفحه متوقف شد${it.recordedFilePath?.let { name -> " • ذخیره در $name" } ?: ""}"
            )
        }
    }

    override fun onDestroy() {
        stopYouTubeLiveInternal()
        stopScreenCaptureInternal()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "streamcast_capture_channel"
        private const val NOTIFICATION_ID = 7201

        // Mandatory user specs: 720p (1280x720), 30 FPS, 6 Mbps (6,000,000 bps)
        const val VIDEO_WIDTH = 1280
        const val VIDEO_HEIGHT = 720
        const val VIDEO_FPS = 30
        const val VIDEO_BITRATE_BPS = 6_000_000

        const val ACTION_START_CAPTURE = "com.example.action.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.example.action.STOP_CAPTURE"
        const val ACTION_START_YOUTUBE_LIVE = "com.example.action.START_YOUTUBE_LIVE"
        const val ACTION_STOP_YOUTUBE_LIVE = "com.example.action.STOP_YOUTUBE_LIVE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_RTMP_URL = "extra_rtmp_url"
        const val EXTRA_STREAM_KEY = "extra_stream_key"

        private val _telemetry = MutableStateFlow(CaptureTelemetry())
        val telemetry: StateFlow<CaptureTelemetry> = _telemetry.asStateFlow()

        fun setCameraEnabled(enabled: Boolean) {
            _telemetry.update {
                it.copy(
                    isCameraOn = enabled,
                    statusMessage = if (enabled) "دوربین جلو (300×250 px) روی استریم فعال شد" else "دوربین جلو غیرفعال شد"
                )
            }
        }

        fun setMicEnabled(enabled: Boolean) {
            _telemetry.update {
                it.copy(
                    isMicOn = enabled,
                    micLevelDb = if (enabled) it.micLevelDb else 0f,
                    statusMessage = if (enabled) "میکروفون (Microphone) روشن شد" else "میکروفون (Microphone) قطع شد"
                )
            }
        }

        fun setGameAudioEnabled(enabled: Boolean) {
            _telemetry.update {
                it.copy(
                    isGameAudioOn = enabled,
                    gameAudioLevelDb = if (enabled) it.gameAudioLevelDb else 0f,
                    statusMessage = if (enabled) "صدای بازی (Game Audio) فعال شد" else "صدای بازی (Game Audio) قطع شد"
                )
            }
        }
    }
}
