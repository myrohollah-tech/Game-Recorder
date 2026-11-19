package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class StreamConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val rtmpUrl: String = "rtmp://a.rtmp.youtube.com/live2",
    val streamKey: String = "",
    val privacyStatus: String = "public",
    val resolution: String = "1280x720",
    val fps: Long = 30L,
    val bitrateKbps: Long = 6000L,
    val cameraEnabled: Boolean = true,
    val micEnabled: Boolean = true,
    val gameAudioEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StreamNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CaptureTelemetry(
    val isScreenCapturing: Boolean = false,
    val isYouTubeLive: Boolean = false,
    val isCameraOn: Boolean = true,
    val isMicOn: Boolean = true,
    val isGameAudioOn: Boolean = true,
    val resolutionLabel: String = "1280×720 (720p)",
    val targetFps: Int = 30,
    val currentFps: Int = 0,
    val targetBitrateMbps: Double = 6.0,
    val currentBitrateKbps: Int = 0,
    val elapsedSeconds: Long = 0L,
    val recordedFilePath: String? = null,
    val recordedBytes: Long = 0L,
    val rtmpPacketsSent: Long = 0L,
    val micLevelDb: Float = 0f,
    val gameAudioLevelDb: Float = 0f,
    val statusMessage: String = "آماده ضبط و پخش زنده (720p • 30 FPS • 6 Mbps)"
)
