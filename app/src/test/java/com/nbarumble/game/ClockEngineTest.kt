package com.nbarumble.game

import com.nbarumble.game.data.model.Room
import com.nbarumble.game.game.ClockEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockEngineTest {

    private val me = "playerA"
    private val opp = "playerB"

    @Test
    fun `inactive player clock stays untouched`() {
        val remaining = ClockEngine.remainingMs(
            storedBankMs = 180_000,
            isActive = false,
            turnStartedAtMs = 1_000,
            nowServerMs = 100_001
        )
        assertEquals(180_000, remaining)
    }

    @Test
    fun `active player clock drains with elapsed time`() {
        val remaining = ClockEngine.remainingMs(
            storedBankMs = 180_000,
            isActive = true,
            turnStartedAtMs = 50_000,
            nowServerMs = 100_000
        )
        assertEquals(130_000, remaining)
    }

    @Test
    fun `active clock never goes below zero`() {
        assertEquals(
            0L,
            ClockEngine.remainingMs(5_000, true, 1_000, 100_000)
        )
    }

    @Test
    fun `elapsed is clamped at zero before turn start`() {
        assertEquals(
            180_000L,
            ClockEngine.remainingMs(180_000, true, 100_000, 50_000)
        )
    }

    @Test
    fun `room helper drains only the active player`() {
        val room = Room(
            player1Id = me,
            player2Id = opp,
            player1TimeLeft = 60_000,
            player2TimeLeft = 45_000,
            activePlayerId = me,
            turnStartedAt = 90_000
        )
        val now = 100_000L
        assertEquals(50_000, ClockEngine.remainingMs(room, me, now))
        assertEquals(45_000, ClockEngine.remainingMs(room, opp, now))
    }

    @Test
    fun `elapsed seconds rounds to whole seconds`() {
        assertEquals(0, ClockEngine.elapsedSeconds(100_000, 99_000))
        assertEquals(1, ClockEngine.elapsedSeconds(100_000, 101_000))
        assertEquals(5, ClockEngine.elapsedSeconds(100_000, 105_999))
    }

    @Test
    fun `clock formats as MM colons SS`() {
        assertEquals("00:00", ClockEngine.formatClock(0))
        assertEquals("00:04", ClockEngine.formatClock(4_900))
        assertEquals("00:05", ClockEngine.formatClock(5_000))
        assertEquals("01:00", ClockEngine.formatClock(60_000))
        assertEquals("03:59", ClockEngine.formatClock(239_400))
    }

    @Test
    fun `clock labels a sub-second run as zero seconds`() {
        assertEquals(0, ClockEngine.elapsedSeconds(0, 999))
        assertEquals(1, ClockEngine.elapsedSeconds(0, 1_000))
    }
}