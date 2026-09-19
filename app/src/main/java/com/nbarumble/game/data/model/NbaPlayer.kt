package com.nbarumble.game.data.model

/**
 * A single NBA player from the bundled roster.
 *
 * @property id NBA player id used to build the free public NBA CDN headshot URL.
 * @property name Full display name, e.g. "LeBron James".
 * @property headshotUrl Public, free-to-use headshot image URL.
 * @property tier Recognizability tier: 1 = icon, 2 = star/veteran, 3 = role/young.
 */
data class NbaPlayer(
    val id: String,
    val name: String,
    val headshotUrl: String,
    val tier: Int = 3
) {
    val firstName: String
        get() = name.trim().substringBefore(' ')

    val lastName: String
        get() {
            val suffix = Regex("(?i)(^|\\s)(jr\\.?|sr\\.?|ii+\\b|iii\\b|iv\\b)$").replace(name.trim(), "")
            return suffix.trim().substringAfterLast(' ')
        }
}