package com.nbarumble.game

import com.nbarumble.game.data.repo.NbaPlayerRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NbaPlayerRepositoryTest {

    @Test
    fun `parses roster and builds free CDN headshot urls`() {
        val raw = """
            {
              "players": [
                {"id": "2544", "name": "LeBron James"},
                {"id": "1629029", "name": "Luka Doncic"}
              ]
            }
        """.trimIndent()

        val players = NbaPlayerRepository.parse(raw)

        assertEquals(2, players.size)
        val lebron = players.first()
        assertEquals("LeBron James", lebron.name)
        assertEquals(
            "https://cdn.nba.com/headshots/nba/latest/1040x760/2544.png",
            lebron.headshotUrl
        )
    }

    @Test
    fun `last name handles jr suffixes`() {
        val raw = """{"players":[{"id":"1628991","name":"Jaren Jackson Jr."}]}"""
        val players = NbaPlayerRepository.parse(raw)
        assertEquals("Jackson", players.first().lastName)
    }
}