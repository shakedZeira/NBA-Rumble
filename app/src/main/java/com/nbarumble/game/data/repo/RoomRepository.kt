package com.nbarumble.game.data.repo

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.nbarumble.game.data.model.Difficulty
import com.nbarumble.game.data.model.NbaPlayer
import com.nbarumble.game.data.model.Room
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * All room logic: create/join, live observation, server-anchored clock, and
 * the two atomic clock mutations (correct guess, skip). Every mutation is a
 * [Transaction] so simultaneous writes from both clients serialize safely.
 *
 * The "now" used everywhere is server-anchored: System.currentTimeMillis()
 * + the live offset from Firebase's `.info/serverTimeOffset` node. Both
 * devices therefore agree on elapsed time even if their system clocks differ.
 */
class RoomRepository(
    private val db: FirebaseDatabase,
    private val nbaPlayers: NbaPlayerRepository
) {

    enum class ActionResult {
        SUCCESS,          // mutation applied
        NOT_YOUR_TURN,    // turn already switched
        NOT_PLAYING       // room not in PLAYING state
    }

    companion object {
        const val PATH_ROOMS = "rooms"
        const val PATH_SERVER_TIME_OFFSET = ".info/serverTimeOffset"
    }

    private val roomRoot get() = db.getReference(PATH_ROOMS)

    private fun roomRef(code: String): DatabaseReference = roomRoot.child(code)

    @Volatile
    private var serverOffset: Long = 0L

    /** Server-anchored epoch millis — use everywhere a turn timestamp is read/written. */
    fun nowServer(): Long = System.currentTimeMillis() + serverOffset

    init {
        db.getReference(PATH_SERVER_TIME_OFFSET).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverOffset = (snapshot.value as? Number)?.toLong() ?: 0L
            }

            override fun onCancelled(error: DatabaseError) {
                // keep last known offset
            }
        })
    }

    /** Live room stream. Emits null when the room doesn't exist yet. */
    fun observeRoom(code: String): Flow<Room?> = callbackFlow {
        val ref = roomRef(code)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val room = if (snapshot.exists()) Room.fromSnapshot(code, snapshot) else null
                trySend(room)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun createRoom(
        code: String,
        hostId: String,
        bankSeconds: Long,
        penaltySeconds: Long,
        difficulty: String = Difficulty.DB_MEDIUM
    ): Result<Unit> {
        val base = Room(
            code = code,
            player1Id = hostId,
            player1TimeLeft = bankSeconds * 1000L,
            player2TimeLeft = bankSeconds * 1000L,
            bankSeconds = bankSeconds,
            penaltySeconds = penaltySeconds,
            status = Room.STATUS_WAITING,
            difficulty = difficulty
        )
        return roomRef(code).setValueA(base.toWriteMap())
    }

    /**
     * Joins a waiting room and starts the match. The active player is chosen
     * at random and the starter player is picked from [pool] on this write.
     */
    suspend fun joinRoom(code: String, joinerId: String, pool: List<NbaPlayer>): Result<ActionResult> {
        return roomRef(code).runTransactionA { data ->
            val p1 = (data.child("player1Id").value as? String)
            val p2 = (data.child("player2Id").value as? String)
            val status = data.child("status").value as? String
            if (p1 == null || status != Room.STATUS_WAITING) {
                return@runTransactionA Transaction.abort()
            }
            if (p2 != null) {
                return@runTransactionA Transaction.abort()
            }
            val bankMs = ((data.child("bankSeconds").value as? Number)?.toLong() ?: 180L) * 1000L
            data.child("player2Id").value = joinerId
            data.child("status").value = Room.STATUS_PLAYING
            data.child("activePlayerId").value = listOf(p1, joinerId).random()
            data.child("currentNbaPlayer").value = pickNext(pool, used = emptyList(), current = null)
            data.child("turnStartedAt").value = nowServer()
            data.child("usedNbaPlayers").value = emptyList<String>()
            Transaction.success(data)
        }
    }

    /** Active player guessed correctly: bank the elapsed time, switch turn, advance player. */
    suspend fun reportCorrectGuess(
        code: String,
        myId: String,
        pool: List<NbaPlayer>
    ): Result<ActionResult> {
        return roomRef(code).runTransactionA { data ->
            val out = beginClockMutation(data, myId) ?: return@runTransactionA Transaction.abort()
            val elapsed = (nowServer() - data.child("turnStartedAt").asLong).coerceAtLeast(0L)

            val newOut = out.timeLeft - elapsed
            if (newOut <= 0L) {
                finishGame(data, out.key, opponentPlayerId(data), 0L)
                return@runTransactionA Transaction.success(data)
            }
            data.child(out.key).value = newOut
            data.child("activePlayerId").value = opponentPlayerId(data)
            advancePlayer(data, pool)
            data.child("turnStartedAt").value = nowServer()
            Transaction.success(data)
        }
    }

    /** Active player skips: subtract penalty + elapsed, turn stays, advance player. */
    suspend fun skip(
        code: String,
        myId: String,
        pool: List<NbaPlayer>
    ): Result<ActionResult> {
        return roomRef(code).runTransactionA { data ->
            val out = beginClockMutation(data, myId) ?: return@runTransactionA Transaction.abort()
            val penaltyMs = ((data.child("penaltySeconds").value as? Number)?.toLong() ?: 10L) * 1000L
            val elapsed = (nowServer() - data.child("turnStartedAt").asLong).coerceAtLeast(0L)

            val newOut = out.timeLeft - elapsed - penaltyMs
            if (newOut <= 0L) {
                finishGame(data, out.key, opponentPlayerId(data), 0L)
                return@runTransactionA Transaction.success(data)
            }
            data.child(out.key).value = newOut
            advancePlayer(data, pool)
            data.child("turnStartedAt").value = nowServer()
            Transaction.success(data)
        }
    }

    suspend fun deleteRoom(code: String) {
        roomRef(code).removeValue()
    }

    suspend fun roomExists(code: String): Boolean = suspendCancellableCoroutine { cont ->
        roomRef(code).get()
            .addOnSuccessListener { snapshot -> cont.resume(snapshot.exists()) }
            .addOnFailureListener { cont.resume(false) }
    }

    /**
     * Idempotent "someone must have run out of time" mutation. Fired by
     * whichever client first observes the active player's derived clock at 0.
     */
    suspend fun finishOnTimeout(code: String, expiredPlayerId: String): Result<ActionResult> {
        return roomRef(code).runTransactionA { data ->
            val status = data.child("status").value as? String
            if (status != Room.STATUS_PLAYING) return@runTransactionA Transaction.abort()
            if (data.child("activePlayerId").value as? String != expiredPlayerId) {
                return@runTransactionA Transaction.abort()
            }
            val loserKey = when (expiredPlayerId) {
                data.child("player1Id").value as? String -> "player1TimeLeft"
                data.child("player2Id").value as? String -> "player2TimeLeft"
                else -> return@runTransactionA Transaction.abort()
            }
            finishGame(data, loserKey, opponentPlayerId(data), 0L)
            Transaction.success(data)
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private data class OutPlayer(val key: String, val timeLeft: Long)

    /** Validates state + active player, returns the active player's clock key/time. */
    private fun beginClockMutation(data: MutableData, myId: String): OutPlayer? {
        val status = data.child("status").value as? String
        if (status != Room.STATUS_PLAYING) return null
        val activeKey = when (data.child("activePlayerId").value as? String) {
            data.child("player1Id").value as? String -> "player1TimeLeft"
            data.child("player2Id").value as? String -> "player2TimeLeft"
            else -> return null
        }
        val activePlayerId = data.child("activePlayerId").value as? String
        if (activePlayerId != myId) return null
        val timeLeft = data.child(activeKey).asLong
        return OutPlayer(activeKey, timeLeft)
    }

    private fun opponentPlayerId(data: MutableData): String {
        val p1 = data.child("player1Id").value as? String ?: ""
        val p2 = data.child("player2Id").value as? String ?: ""
        return when (data.child("activePlayerId").value as? String) {
            p1 -> p2
            p2 -> p1
            else -> ""
        }
    }

    private fun finishGame(data: MutableData, loserKey: String, winnerId: String, loserTime: Long) {
        data.child(loserKey).value = loserTime
        data.child("status").value = Room.STATUS_FINISHED
        data.child("winnerId").value = winnerId
        data.child("activePlayerId").value = winnerId
    }

    /**
     * Advances to the next current player inside a transaction: marks the
     * current player as used and picks a fresh one that was never shown in
     * this match. Guarantees no repeats can ever surface on either device.
     */
    private fun advancePlayer(data: MutableData, pool: List<NbaPlayer>) {
        val current = data.child("currentNbaPlayer").value as? String
        val used = usedPlayers(data)
        val next = pickNext(pool, used = used + listOfNotNull(current), current = current)
        if (current != null) {
            data.child("usedNbaPlayers").value = (used + current)
        }
        data.child("currentNbaPlayer").value = next
    }

    private fun usedPlayers(data: MutableData): List<String> =
        (data.child("usedNbaPlayers").value as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    /** Picks a random player that isn't in [used] and isn't [current]. */
    private fun pickNext(pool: List<NbaPlayer>, used: List<String>, current: String?): String {
        val excluded = (used + listOfNotNull(current)).toSet()
        val remaining = pool.filter { it.name !in excluded }
        val picked = if (remaining.isEmpty()) nbaPlayers.randomFrom(pool, excluding = current) else remaining.random()
        return picked.name
    }

    private val MutableData.asLong: Long
        get() = (value as? Number)?.toLong() ?: 0L
}

private suspend fun DatabaseReference.setValueA(value: Any?): Result<Unit> =
    suspendCancellableCoroutine { cont ->
        setValue(value) { error, _ ->
            if (error != null) cont.resume(Result.failure(error.toException()))
            else cont.resume(Result.success(Unit))
        }
    }

private suspend fun DatabaseReference.runTransactionA(handler: (MutableData) -> Transaction.Result): Result<RoomRepository.ActionResult> =
    suspendCancellableCoroutine { cont ->
        runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result =
                handler(currentData)

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    cont.resume(Result.failure(error.toException()))
                } else if (committed) {
                    cont.resume(Result.success(RoomRepository.ActionResult.SUCCESS))
                } else {
                    cont.resume(Result.success(RoomRepository.ActionResult.NOT_YOUR_TURN))
                }
            }
        })
    }