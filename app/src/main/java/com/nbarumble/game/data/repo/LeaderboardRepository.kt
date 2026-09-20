package com.nbarumble.game.data.repo

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.nbarumble.game.data.model.LeaderboardEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Tracks multiplayer match wins under leaderboard/{uid} = { name, wins }.
 * Reads use a single live listener; writes are a best-effort counter
 * transaction so simultaneous wins from both clients serialize safely.
 */
class LeaderboardRepository(private val db: FirebaseDatabase) {

    companion object {
        const val PATH_LEADERBOARD = "leaderboard"
    }

    private val leaderboardRef get() = db.getReference(PATH_LEADERBOARD)

    /** Best-effort: increments the winner's stash or seeds it on first win. */
    suspend fun recordWin(uid: String) {
        if (uid.isBlank()) return
        val displayName = "Player " + uid.takeLast(4).uppercase()
        leaderboardRef.child(uid).incrementWinTxn(displayName)
    }

    /** Live top [limit] wins, newest order by descending win count. */
    fun observeTop(limit: Int = 20): Flow<List<LeaderboardEntry>> = callbackFlow {
        val ref = leaderboardRef.orderByChild("wins").limitToLast(limit)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = if (!snapshot.exists()) {
                    emptyList()
                } else {
                    snapshot.children.mapNotNull { child ->
                        val uid = child.key ?: return@mapNotNull null
                        val name = child.child("name").value as? String ?: return@mapNotNull null
                        val wins = (child.child("wins").value as? Number)?.toLong() ?: 0L
                        LeaderboardEntry(uid = uid, displayName = name, wins = wins)
                    }.sortedByDescending { it.wins }
                }
                trySend(entries)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}

private suspend fun DatabaseReference.incrementWinTxn(displayName: String) {
    suspendCancellableCoroutine { cont ->
        runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (!currentData.hasChildren()) {
                    currentData.child("name").value = displayName
                    currentData.child("wins").value = 1L
                } else {
                    val wins = (currentData.child("wins").value as? Number)?.toLong() ?: 0L
                    currentData.child("wins").value = wins + 1L
                }
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                cont.resume(Unit)
            }
        })
    }
}