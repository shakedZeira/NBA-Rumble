package com.nbarumble.game.game

import com.nbarumble.game.data.model.NbaPlayer

/**
 * Validates spoken guesses against NBA player names using fuzzy matching.
 *
 * Tolerant of common speech-recognition slips: mumbled endings, dropped
 * suffixes ("Jr.", "III"), "De'Aaron" vs "Deaaron", one-name guesses
 * ("LeBron" for LeBron James), and light mispronunciations.
 *
 * Phonetic [PRONUNCIATION_ALIASES] cover the names that recognizers and
 * players routinely mangle (e.g. "Shai Gilgeous-Alexander", "Jrue Holiday").
 */
class FuzzyMatcher {

    data class Result(
        val isMatch: Boolean,
        val matchedPlayer: NbaPlayer?,
        val spoken: String,
        val distance: Int
    )

    fun match(spoken: String, player: NbaPlayer): Result {
        val cleanSpoken = normalize(spoken)
        if (cleanSpoken.length < MIN_SPOKEN_LENGTH) {
            // Very short answers ("ja", "obi") are valid only when they line up
            // with a short name part; otherwise they match nothing.
            return matchLenient(cleanSpoken, player)
        }

        val candidates = buildList {
            add(player.name)
            add(player.lastName)
            add(player.firstName)
            addAll(PRONUNCIATION_ALIASES[normalize(player.name)].orEmpty())
        }.distinct().map { normalize(it) }

        var best = Int.MAX_VALUE
        for (candidate in candidates) {
            if (candidate.isEmpty()) continue
            val d = levenshtein(cleanSpoken, candidate)
            val threshold = thresholdFor(candidate.length)
            if (d <= threshold) {
                best = minOf(best, d)
            }
        }
        return Result(isMatch = best != Int.MAX_VALUE, matchedPlayer = player, spoken = spoken, distance = best)
    }

    /**
     * Matches against every recognition hypothesis and returns the closest
     * (e.g. a garbled first guess is ignored when a later one fits).
     */
    fun matchAny(texts: List<String>, player: NbaPlayer): Result {
        var best: Result? = null
        for (text in texts) {
            val r = match(text, player)
            if (r.isMatch && (best == null || !best.isMatch || r.distance < best.distance)) {
                best = r
            }
        }
        return best ?: Result(false, null, texts.firstOrNull().orEmpty(), Int.MAX_VALUE)
    }

    /**
     * Fallback for very short names: compare against the shortest name-part
     * so "ja" for Ja Morant still passes its own length requirement.
     */
    private fun matchLenient(cleanSpoken: String, player: NbaPlayer): Result {
        if (cleanSpoken.isEmpty()) return Result(false, null, "", Int.MAX_VALUE)
        val candidates = buildList {
            add(player.name)
            add(player.lastName)
            add(player.firstName)
            addAll(PRONUNCIATION_ALIASES[normalize(player.name)].orEmpty())
        }.map { normalize(it) }.filter { it.isNotEmpty() && it.length >= cleanSpoken.length }
        var best = Int.MAX_VALUE
        for (candidate in candidates) {
            val d = levenshtein(cleanSpoken, candidate)
            if (d <= thresholdFor(candidate.length)) best = minOf(best, d)
        }
        return Result(isMatch = best != Int.MAX_VALUE, matchedPlayer = player, spoken = "", distance = best)
    }

    /** Normalized form used for comparison. */
    internal fun normalize(raw: String): String {
        var s = raw.lowercase().trim()
        // strip suffixes: ", jr.", " jr", ", sr.", " iii", " ii", " iv", "'s" etc.
        s = s.replace(Regex(",?\\s*(jr\\.?|sr\\.?|ii+\\b|iii\\b|iv\\b)\\.?$"), "")
        // strip leading initials: "d." "k. m."
        s = s.replace(Regex("(^|\\s)[a-z]\\.\\s*"), "")
        // drop apostrophes and separators, keep letters only
        s = s.replace("[^a-z]".toRegex(), "")
        return s
    }

    /** Classic Levenshtein edit distance. */
    internal fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    private fun thresholdFor(candidateLen: Int): Int =
        maxOf(1, (candidateLen * 0.25).toInt())

    companion object {
        private const val MIN_SPOKEN_LENGTH = 3

        /**
         * Extra accepted spellings/pronunciations, keyed by normalized full
         * name. Includes the forms Google's recognizer most often produces.
         */
        val PRONUNCIATION_ALIASES: Map<String, List<String>> = mapOf(
            "shaigilgeousalexander" to listOf(
                "shai", "sga",
                "shai gilgeous alexander",
                "shai gillius alexander",
                "shai gillious alexander",
                "shai gilgous alexander",
                "gilgeous alexander"
            ),
            "jrueholiday" to listOf(
                "jrue", "drew holiday", "joo holiday", "drew", "true holiday"
            ),
            "jamorant" to listOf(
                "ja", "morant", "jay morant"
            ),
            "obitoppin" to listOf(
                "obi", "toppin", "obie", "obie toppin", "obi topin", "opi toppin"
            ),
            "landryshamet" to listOf(
                "landry", "shamet"
            ),
            "deniavdija" to listOf(
                "deni", "avdija", "denni", "dennie", "denni avdija", "deni avdiya", "deni avdia", "danny avdija"
            ),
            "bensaraf" to listOf(
                "ben", "saraf", "ben seraf"
            ),
            "dannywolf" to listOf(
                "danny", "wolf", "daniel wolf"
            )
        )
    }
}