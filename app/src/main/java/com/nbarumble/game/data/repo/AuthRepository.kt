package com.nbarumble.game.data.repo

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Anonymous Firebase authentication — free tier, no sign-up friction.
 */
class AuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /** Firebase UID of the signed-in anonymous user, or null. */
    val uid: String? get() = auth.currentUser?.uid

    suspend fun ensureSignedIn(): Result<String> {
        auth.currentUser?.let { return Result.success(it.uid) }
        return suspendCancellableCoroutine { cont ->
            auth.signInAnonymously()
                .addOnSuccessListener {
                    auth.currentUser?.let { user -> cont.resume(Result.success(user.uid)) }
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(Result.failure(it))
                }
        }
    }

    fun signOut() {
        auth.signOut()
    }
}