package net.casual.bot.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.properties.Properties
import net.casual.bot.CasualBot
import kotlin.io.path.Path
import kotlin.io.path.readLines

@Serializable
data class Env(
    @SerialName("BOT_TOKEN")
    val botToken: String = "",
    @SerialName("DB_CANON_NAME")
    val databaseCanonName: String = "",
    @SerialName("DB_TWISTED_NAME")
    val databaseTwistedName: String = "",
    @SerialName("DB_URL")
    val databaseUrl: String = "",
    @SerialName("DB_USERNAME")
    val databaseUsername: String? = null,
    @SerialName("DB_PASSWORD")
    val databasePassword: String? = null,
    @SerialName("GUILD_ID")
    val guildId: Long = 0,
    @SerialName("ORGANIZER_ID")
    val organizerId: Long = 0L,
    @SerialName("LOCAL")
    val local: Boolean = false
) {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun read(): Env {
            try {
                val env = Path(".env").readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .associate {
                        val (key, value) = it.split("=", limit = 2)
                        key.trim() to value.trim().removeSurrounding("\"")
                    }
                return Properties.decodeFromStringMap(serializer(), env)
            } catch (e: Exception) {
                CasualBot.logger.error(e) { "Failed to read env" }
            }
            return Env()
        }
    }
}