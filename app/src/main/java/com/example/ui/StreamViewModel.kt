package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CaptureTelemetry
import com.example.data.StreamConfig
import com.example.data.StreamNote
import com.example.data.StreamRepository
import com.example.service.ScreenCaptureService
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class StreamViewModel(
    private val repository: StreamRepository,
    private val currentUserId: String
) : ViewModel() {

    val telemetry: StateFlow<CaptureTelemetry> = ScreenCaptureService.telemetry

    private val _selectedConfigId = MutableStateFlow<String?>(null)
    val selectedConfigId: StateFlow<String?> = _selectedConfigId.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val configsState: StateFlow<UiState<List<StreamConfig>>> = repository.observeStreamConfigs(currentUserId)
        .map<List<StreamConfig>, UiState<List<StreamConfig>>> { list ->
            val sorted = list.sortedByDescending { it.createdAt?.seconds ?: 0L }
            if (_selectedConfigId.value == null && sorted.isNotEmpty()) {
                _selectedConfigId.value = sorted.first().id
            }
            UiState.Success(sorted)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.message ?: "خطا در دریافت لیست استریم‌ها"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val notesState: StateFlow<UiState<List<StreamNote>>> = repository.observeStreamNotes(currentUserId)
        .map<List<StreamNote>, UiState<List<StreamNote>>> { list ->
            val sorted = list.sortedByDescending { it.createdAt?.seconds ?: 0L }
            UiState.Success(sorted)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.message ?: "خطا در دریافت یادداشت‌ها"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun selectStreamConfig(config: StreamConfig) {
        _selectedConfigId.value = config.id
        ScreenCaptureService.setCameraEnabled(config.cameraEnabled)
        ScreenCaptureService.setMicEnabled(config.micEnabled)
        ScreenCaptureService.setGameAudioEnabled(config.gameAudioEnabled)
        _actionMessage.value = "استریم «${config.title}» انتخاب شد"
    }

    fun createYouTubeStreamProfile(
        title: String,
        description: String,
        rtmpUrl: String,
        streamKey: String,
        privacyStatus: String
    ) {
        viewModelScope.launch {
            val current = telemetry.value
            val result = repository.createStreamConfig(
                title = title,
                description = description,
                rtmpUrl = rtmpUrl,
                streamKey = streamKey,
                privacyStatus = privacyStatus,
                cameraEnabled = current.isCameraOn,
                micEnabled = current.isMicOn,
                gameAudioEnabled = current.isGameAudioOn
            )
            result.onSuccess { id ->
                _selectedConfigId.value = id
                _actionMessage.value = "استریم جدید YouTube Live با موفقیت ایجاد و انتخاب شد"
            }.onFailure { err ->
                _actionMessage.value = "خطا در ذخیره استریم: ${err.localizedMessage}"
            }
        }
    }

    fun deleteStreamProfile(configId: String) {
        viewModelScope.launch {
            repository.deleteStreamConfig(configId)
            if (_selectedConfigId.value == configId) {
                _selectedConfigId.value = null
            }
        }
    }

    fun addQuickNote(title: String, content: String) {
        viewModelScope.launch {
            repository.createStreamNote(
                title = title,
                content = content,
                isPinned = true
            ).onSuccess {
                _actionMessage.value = "یادداشت استریم ذخیره شد"
            }
        }
    }

    fun deleteQuickNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteStreamNote(noteId)
        }
    }

    fun toggleCamera() {
        val next = !telemetry.value.isCameraOn
        ScreenCaptureService.setCameraEnabled(next)
    }

    fun toggleMicrophone() {
        val next = !telemetry.value.isMicOn
        ScreenCaptureService.setMicEnabled(next)
    }

    fun toggleGameAudio() {
        val next = !telemetry.value.isGameAudioOn
        ScreenCaptureService.setGameAudioEnabled(next)
    }

    fun clearMessage() {
        _actionMessage.value = null
    }
}
