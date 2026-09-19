package com.nbarumble.game.data.model

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Exclude

/**
 * Mirrors the Firebase Realtime Database room node.
 *
 * Story (recommended):
 *   rooms/{code} {
 *     player1Id, player2Id,
 *     player1TimeLeft, player2TimeLeft (ms),
 *     activePlayerId,
 *     currentNbaPlayer (player name),
 *     turnStartedAt (server-ms anchor),
 *     bankSeconds, penaltySeconds (config, set by host),
 *     status: WAITING | PLAYING | FINISHED,
 *     winnerId
 *   }
 */
data class Room(
    val code: String = "",
    val player1Id: String? = null,
    val player2Id: String? = null,
    val player1TimeLeft: Long = 0L,
    val player2TimeLeft: Long = 0L,
    val activePlayerId: String? = null,
    val currentNbaPlayer: String? = null,
    val turnStartedAt: Long = 0L,
    val bankSeconds: Long = 180L,
    val penaltySeconds: Long = 10L,
    val status: String = STATUS_WAITING,
    val winnerId: String? = null,
    val usedNbaPlayers: List<String> = emptyList(),
    val difficulty: String = Difficulty.DB_MEDIUM
) {

    val isWaiting: Boolean get() = status == STATUS_WAITING
    val isPlaying: Boolean get() = status == STATUS_PLAYING
    val isFinished: Boolean get() = status == STATUS_FINISHED

    val opponentOf: String?
        get() = null

    fun playerIdForSide(hostId: String): String? = when {
        player1Id == hostId -> player1Id
        player2Id == hostId -> player2Id
        else -> null
    }

    fun timeLeftFor(playerId: String): Long = when (playerId) {
        player1Id -> player1TimeLeft
        player2Id -> player2TimeLeft
        else -> 0L
    }

    fun isActive(playerId: String): Boolean = activePlayerId == playerId

    @get:Exclude
    val playerCount: Int
        get() = listOfNotNull(player1Id, player2Id).size

    fun toWriteMap(): MutableMap<String, Any?> = mutableMapOf(
        "player1Id" to player1Id,
        "player2Id" to player2Id,
        "player1TimeLeft" to player1TimeLeft,
        "player2TimeLeft" to player2TimeLeft,
        "activePlayerId" to activePlayerId,
        "currentNbaPlayer" to currentNbaPlayer,
        "turnStartedAt" to turnStartedAt,
        "bankSeconds" to bankSeconds,
        "penaltySeconds" to penaltySeconds,
        "status" to status,
        "winnerId" to winnerId,
        "usedNbaPlayers" to usedNbaPlayers,
        "difficulty" to difficulty
    )

    companion object {
        const val STATUS_WAITING = "WAITING"
        const val STATUS_PLAYING = "PLAYING"
        const val STATUS_FINISHED = "FINISHED"

        fun fromSnapshot(code: String, snapshot: DataSnapshot): Room {
            fun any(key: String) = snapshot.child(key).value
            return Room(
                code = code,
                player1Id = any("player1Id") as? String,
                player2Id = any("player2Id") as? String,
                player1TimeLeft = (any("player1TimeLeft") as? Number)?.toLong() ?: 0L,
                player2TimeLeft = (any("player2TimeLeft") as? Number)?.toLong() ?: 0L,
                activePlayerId = any("activePlayerId") as? String,
                currentNbaPlayer = any("currentNbaPlayer") as? String,
                turnStartedAt = (any("turnStartedAt") as? Number)?.toLong() ?: 0L,
                bankSeconds = (any("bankSeconds") as? Number)?.toLong() ?: 180L,
                penaltySeconds = (any("penaltySeconds") as? Number)?.toLong() ?: 10L,
                status = any("status") as? String ?: STATUS_WAITING,
                winnerId = any("winnerId") as? String,
                usedNbaPlayers = (any("usedNbaPlayers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                difficulty = (any("difficulty") as? String) ?: Difficulty.DB_MEDIUM
            )
        }
    }
}