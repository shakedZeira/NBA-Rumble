package com.nbarumble.game.game

import com.nbarumble.game.data.model.Room
import java.util.concurrent.TimeUnit

/**
 * Pure chess-clock math. All "now" values must come from the same
 * server-anchored clock (System.currentTimeMillis() + serverTimeOffset)
 * so both devices agree on how much of a bank has been consumed.
 */
object ClockEngine {

    /**
     * Remaining time for a player, in ms.
     *
     * Only the active player's bank decreases while their turn runs:
     * `storedBank - (now - turnStartedAt)`. The inactive player keeps the
     * stored value untouched.
     */
    fun remainingMs(
        storedBankMs: Long,
        isActive: Boolean,
        turnStartedAtMs: Long,
        nowServerMs: Long
    ): Long {
        if (!isActive) return storedBankMs
        val elapsed = (nowServerMs - turnStartedAtMs).coerceAtLeast(0L)
        return (storedBankMs - elapsed).coerceAtLeast(0L)
    }

    fun remainingMs(room: Room, playerId: String, nowServerMs: Long): Long =
        remainingMs(
            storedBankMs = room.timeLeftFor(playerId),
            isActive = room.isActive(playerId),
            turnStartedAtMs = room.turnStartedAt,
            nowServerMs = nowServerMs
        )

    /** Seconds consumed by the current turn regardless of sign. */
    fun elapsedSeconds(turnStartedAtMs: Long, nowServerMs: Long): Long =
        ((nowServerMs - turnStartedAtMs).coerceAtLeast(0L)) / 1000L

    /** Formats ms as `MM:SS`, flooring to the displayed second (shot-clock style). */
    fun formatClock(ms: Long): String {
        val total = (ms.coerceAtLeast(0L)) / 1000L
        val minutes = total / 60L
        val seconds = total % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    fun countdownFromNow(nowServerMs: Long): Long = nowServerMs

    /** Convenience for calling code that prefers times in milliseconds. */
    fun msToSeconds(ms: Long): Long = TimeUnit.MILLISECONDS.toSeconds(ms)

    fun secondsToMs(seconds: Long): Long = TimeUnit.SECONDS.toMillis(seconds)
}