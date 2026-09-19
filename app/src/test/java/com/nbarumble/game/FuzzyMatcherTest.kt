package com.nbarumble.game

import com.nbarumble.game.data.model.NbaPlayer
import com.nbarumble.game.game.FuzzyMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    private val matcher = FuzzyMatcher()

    private fun player(name: String) = NbaPlayer(id = "1", name = name, headshotUrl = "")

    @Test
    fun `recognizes common spellings and pronunciations`() {
        val sga = player("Shai Gilgeous-Alexander")
        assertTrue(matcher.match("shai gilgeous alexander", sga).isMatch)
        assertTrue(matcher.match("shai gilgeous-alexander", sga).isMatch)
        assertTrue(matcher.match("shai gillius alexander", sga).isMatch)
        assertTrue(matcher.match("sga", sga).isMatch)
        assertTrue(matcher.match("shai", sga).isMatch)

        val jrue = player("Jrue Holiday")
        assertTrue(matcher.match("drew holiday", jrue).isMatch)
        assertTrue(matcher.match("jrue holiday", jrue).isMatch)
        assertTrue(matcher.match("jrue", jrue).isMatch)

        assertTrue(matcher.match("ja", player("Ja Morant")).isMatch)
        assertTrue(matcher.match("ja morant", player("Ja Morant")).isMatch)
        assertTrue(matcher.match("obi", player("Obi Toppin")).isMatch)
        assertTrue(matcher.match("toppin", player("Obi Toppin")).isMatch)
        assertTrue(matcher.match("obie", player("Obi Toppin")).isMatch)
        assertTrue(matcher.match("obie toppin", player("Obi Toppin")).isMatch)
        assertTrue(matcher.match("obi topin", player("Obi Toppin")).isMatch)
        assertTrue(matcher.match("shamet", player("Landry Shamet")).isMatch)
        assertTrue(matcher.match("landry", player("Landry Shamet")).isMatch)
    }

    @Test
    fun `matchAny accepts the correct hypothesis among several guesses`() {
        val obi = player("Obi Toppin")
        val deni = player("Deni Avdija")

        val obiHit = matcher.matchAny(listOf("league of coffee", "ober", "obie toppin"), obi)
        assertTrue(obiHit.isMatch)
        assertTrue(obiHit.matchedPlayer == obi)

        val deniHit = matcher.matchAny(listOf("blann", "denie", "denni avdija"), deni)
        assertTrue(deniHit.isMatch)
        assertTrue(deniHit.matchedPlayer == deni)

        assertFalse(
            matcher.matchAny(
                listOf("portion control", "green day", "seattle"),
                player("Klay Thompson")
            ).isMatch
        )

        val firstActive = player("Stephen Curry")
        assertTrue(matcher.matchAny(listOf("curry", "totally wrong gibberish"), firstActive).isMatch)
    }

    @Test
    fun `recognizes israeli players by both parts`() {
        val deni = player("Deni Avdija")
        assertTrue(matcher.match("deni", deni).isMatch)
        assertTrue(matcher.match("avdija", deni).isMatch)
        assertTrue(matcher.match("deni avdija", deni).isMatch)
        assertTrue(matcher.match("denni", deni).isMatch)
        assertTrue(matcher.match("dennie", deni).isMatch)
        assertTrue(matcher.match("denni avdija", deni).isMatch)
        assertTrue(matcher.match("deni avdiya", deni).isMatch)
        assertTrue(matcher.match("deni avdia", deni).isMatch)
        assertTrue(matcher.match("danny avdija", deni).isMatch)

        val ben = player("Ben Saraf")
        assertTrue(matcher.match("ben", ben).isMatch)
        assertTrue(matcher.match("saraf", ben).isMatch)
        assertTrue(matcher.match("ben seraf", ben).isMatch)

        val wolf = player("Danny Wolf")
        assertTrue(matcher.match("danny", wolf).isMatch)
        assertTrue(matcher.match("wolf", wolf).isMatch)
        assertTrue(matcher.match("daniel wolf", wolf).isMatch)
    }

    @Test
    fun `still accepts one-part guesses for existing stars`() {
        assertTrue(matcher.match("james", player("LeBron James")).isMatch)
        assertTrue(matcher.match("curry", player("Stephen Curry")).isMatch)
        assertTrue(matcher.match("yokic", player("Nikola Jokic")).isMatch)
    }

    @Test
    fun `rejects clearly wrong names`() {
        val sga = player("Shai Gilgeous-Alexander")
        assertFalse(matcher.match("lebron james", sga).isMatch)
        assertFalse(matcher.match("stephen curry", sga).isMatch)
        assertFalse(matcher.match("xyz abcdefghij", player("Anthony Edwards")).isMatch)
    }
}