package com.nbarumble.game.data.repo

import android.content.Context
import com.nbarumble.game.data.model.Difficulty
import com.nbarumble.game.data.model.NbaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Loads the bundled free roster (assets/nba_players.json) and hands out
 * random players / lookups for the game.
 */
class NbaPlayerRepository(context: Context) {

    private val assetManager = context.assets

    private var cache: List<NbaPlayer>? = null

    @Volatile
    private var loading = false

    suspend fun players(): List<NbaPlayer> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            synchronized(this@NbaPlayerRepository) {
                cache?.let { return@withContext it }
                val json = assetManager.open(FILE_NAME).bufferedReader().use { it.readText() }
                parse(json).also { cache = it }
            }
        }
    }

    /** Roster filtered down to the players allowed by [difficulty]. */
    suspend fun players(difficulty: Difficulty): List<NbaPlayer> =
        players().filter { it.tier <= difficulty.maxTier }

    /**
     * Roster filtered by [difficulty] and, when [league] is given, to a single
     * league (NbaPlayer.LEAGUE_NBA / LEAGUE_EUROLEAGUE).
     */
    suspend fun players(difficulty: Difficulty, league: String? = null): List<NbaPlayer> =
        players().filter {
            it.tier <= difficulty.maxTier && (league == null || it.league == league)
        }

    suspend fun randomPlayer(pool: List<NbaPlayer>, excluding: String? = null): NbaPlayer =
        randomFrom(pool, excluding)

    fun randomFrom(pool: List<NbaPlayer>, excluding: String? = null): NbaPlayer {
        if (pool.isEmpty()) return NbaPlayer("0", "Unknown", "")
        val candidates = pool.filter { it.name != excluding }
        return if (candidates.isEmpty()) pool.random()
        else candidates.random()
    }

    fun byName(name: String, pool: List<NbaPlayer>): NbaPlayer? =
        pool.firstOrNull { it.name.equals(name, ignoreCase = true) }

    companion object {
        private const val FILE_NAME = "nba_players.json"
        private const val HEADSHOT_PREFIX = "https://cdn.nba.com/headshots/nba/latest/1040x760/"

        internal fun parse(raw: String): List<NbaPlayer> {
            val root = JSONObject(raw)
            val array = root.getJSONArray("players")
            return buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.getString("id")
                    val name = item.getString("name")
                    add(
                        NbaPlayer(
                            id = id,
                            name = name,
                            headshotUrl = "$HEADSHOT_PREFIX$id.png",
                            tier = item.optInt("tier", 3),
                            league = item.optString("league", NbaPlayer.LEAGUE_NBA),
                            photoUrl = item.optString("photo", "").takeIf { it.isNotEmpty() }
                        )
                    )
                }
            }
        }
    }
}