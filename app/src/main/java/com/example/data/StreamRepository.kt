package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StreamRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeStreamConfigs(userId: String = requireUserId()): Flow<List<StreamConfig>> = flow {
        val path = "stream_configs"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StreamConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getStreamConfigById(configId: String): Result<StreamConfig> = runCatching {
        val docRef = db.collection("stream_configs").document(configId)
        try {
            val snap = docRef.get().await()
            snap.toObject(StreamConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw IllegalStateException("StreamConfig not found")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun createStreamConfig(
        title: String,
        description: String,
        rtmpUrl: String = "rtmp://a.rtmp.youtube.com/live2",
        streamKey: String,
        privacyStatus: String = "public",
        cameraEnabled: Boolean = true,
        micEnabled: Boolean = true,
        gameAudioEnabled: Boolean = true
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("stream_configs").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(120).ifEmpty { "YouTube Game Stream" },
            "description" to description.trim().take(500),
            "rtmpUrl" to rtmpUrl.trim().take(200).ifEmpty { "rtmp://a.rtmp.youtube.com/live2" },
            "streamKey" to streamKey.trim().take(150),
            "privacyStatus" to if (privacyStatus in listOf("public", "unlisted", "private")) privacyStatus else "public",
            "resolution" to "1280x720",
            "fps" to 30L,
            "bitrateKbps" to 6000L,
            "cameraEnabled" to cameraEnabled,
            "micEnabled" to micEnabled,
            "gameAudioEnabled" to gameAudioEnabled,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateStreamConfig(config: StreamConfig): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(config.id.isNotBlank()) { "Config ID must not be blank" }
        requireNotNull(config.createdAt) { "Config createdAt must not be null" }
        val docRef = db.collection("stream_configs").document(config.id)
        try {
            docRef.set(config.copy(userId = uid, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStreamConfig(configId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("stream_configs").document(configId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeStreamNotes(userId: String = requireUserId()): Flow<List<StreamNote>> = flow {
        val path = "stream_notes"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StreamNote::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createStreamNote(
        title: String,
        content: String,
        isPinned: Boolean = true
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("stream_notes").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(100).ifEmpty { "یادداشت استریم" },
            "content" to content.trim().take(1000).ifEmpty { "..." },
            "isPinned" to isPinned,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStreamNote(noteId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("stream_notes").document(noteId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
