package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveStreamConfig_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = StreamRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createStreamConfig(
                title = "Pro Gaming 720p Live",
                description = "30fps 6Mbps stream",
                streamKey = "live2-key-1234"
            )
        }
        assertTrue(createResult.isSuccess)
        val configId = createResult.getOrThrow()

        val configs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStreamConfigs(uid).first { list -> list.any { it.id == configId } }
        }
        assertTrue(configs.any { it.id == configId && it.fps == 30L && it.bitrateKbps == 6000L })
    }

    @Test
    fun getStreamConfigById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = StreamRepository(firestore)
        val configId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createStreamConfig(
                title = "Alice Private Stream",
                description = "Secret",
                streamKey = "key-alice"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = StreamRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getStreamConfigById(configId)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeStreamNotes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = StreamRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeStreamNotes("unauthenticated_uid").first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val firestoreException = caughtException?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caughtException", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_streamer@test.com"
        const val BOB_EMAIL = "bob_streamer@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
