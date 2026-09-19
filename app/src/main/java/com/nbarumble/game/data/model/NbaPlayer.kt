package com.nbarumble.game.data.model

/**
 * A single player from the bundled roster.
 *
 * @property id NBA player id used to build the free public NBA CDN headshot URL
 *   (empty for players who never played in the NBA — they always ship a [photoUrl]).
 * @property name Full display name, e.g. "LeBron James".
 * @property headshotUrl Public, free-to-use NBA CDN headshot for league "NBA"
 *   players. May not exist on the CDN for non-NBA players.
 * @property tier Recognizability tier: 1 = icon, 2 = star/veteran, 3 = role/young.
 * @property league League the player belongs to: "NBA" or "EuroLeague".
 * @property photoUrl Optional override image URL (used for EuroLeague-only
 *   players who have no NBA CDN headshot). Falls back to [headshotUrl].
 */
data class NbaPlayer(
    val id: String,
    val name: String,
    val headshotUrl: String,
    val tier: Int = 3,
    val league: String = "NBA",
    val photoUrl: String? = null
) {
    /** The image to actually render: explicit photo wins over the CDN headshot. */
    val displayUrl: String
        get() = photoUrl ?: headshotUrl

    val isEuroLeague: Boolean
        get() = league == LEAGUE_EUROLEAGUE

    val firstName: String
        get() = name.trim().substringBefore(' ')

    val lastName: String
        get() {
            val suffix = Regex("(?i)(^|\\s)(jr\\.?|sr\\.?|ii+\\b|iii\\b|iv\\b)$").replace(name.trim(), "")
            return suffix.trim().substringAfterLast(' ')
        }

    companion object {
        const val LEAGUE_NBA = "NBA"
        const val LEAGUE_EUROLEAGUE = "EuroLeague"
    }
}