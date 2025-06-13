package net.casual.bot.util

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.senseiwells.mojank.CachedMojank
import me.senseiwells.mojank.MojankEndpoints
import me.senseiwells.mojank.MojankResult
import me.senseiwells.mojank.SimpleMojankProfile
import net.casual.database.CasualDatabase
import net.casual.database.DiscordPlayer
import net.casual.stat.FormattedStat
import net.casual.stat.UnresolvedPlayerStat
import net.casual.util.Named
import java.util.*

object DatabaseUtils {
    private val mojank = CachedMojank(endpoints = MojankEndpoints.ALTERNATE)

    suspend fun getSimpleMojangProfile(username: String): MojankResult<SimpleMojankProfile> {
        return mojank.attempt(3) {
            usernameToSimpleProfile(username)
        }
    }

    suspend fun CasualDatabase.getOrCreateDiscordPlayer(username: String): DiscordPlayer? {
        var player = getDiscordPlayer(username)
        if (player != null) {
            return player
        }
        val profile = getSimpleMojangProfile(username).getOrNull() ?: return null
        player = getDiscordPlayer(profile.id)
        if (player != null) {
            transaction {
                player.name = profile.name
            }
            return player
        }
        return transaction {
            DiscordPlayer.new(profile.id) {
                name = profile.name
                team = null
            }
        }
    }

    suspend fun CasualDatabase.getOrCreateDiscordPlayer(uuid: UUID): DiscordPlayer? {
        val player = getDiscordPlayer(uuid)
        if (player != null) {
            return player
        }
        val username = mojank.attempt(3) {
            uuidToUsername(uuid)
        }.getOrNull() ?: return null
        return transaction {
            DiscordPlayer.new(uuid) {
                name = username
                team = null
            }
        }
    }

    suspend fun <T: Any> CasualDatabase.resolveScoreboard(
        scoreboard: List<UnresolvedPlayerStat<out T>>,
        mapper: (T) -> FormattedStat = FormattedStat.Companion::of
    ): List<Named<FormattedStat>> = coroutineScope {
        scoreboard.map { (uuid, stat) ->
            async { getOrCreateDiscordPlayer(uuid) } to mapper(stat)
        }.mapNotNull { (deferred, stat) ->
            val player = deferred.await() ?: return@mapNotNull null
            Named(player.name, stat)
        }
    }
}