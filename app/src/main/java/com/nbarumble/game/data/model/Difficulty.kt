package com.nbarumble.game.data.model

/**
 * Game difficulty controls how deep the roster goes:
 *  - EASY   -> only tier-1 icons (universally recognizable legends/stars)
 *  - MEDIUM -> tier 1 + 2 (adds stars and solid veterans)
 *  - HARD   -> full roster (role players and young talent included)
 * Also supplies sensible bank/penalty presets for the host.
 */
enum class Difficulty(
    val maxTier: Int,
    val defaultBankSeconds: Long,
    val defaultPenaltySeconds: Long,
    val label: String
) {
    EASY(1, 240L, 5L, "Easy"),
    MEDIUM(2, 180L, 10L, "Medium"),
    HARD(3, 90L, 12L, "Hard");

    companion object {
        const val DB_MEDIUM = "MEDIUM"

        fun fromDb(value: String?): Difficulty =
            entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}