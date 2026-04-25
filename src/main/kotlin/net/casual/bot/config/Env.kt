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
    @SerialName("DB_NAME")
    val databaseName: String = "",
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
    @SerialName("WINS_CHANNEL_ID")
    val winsChannel: Long = 0,
    @SerialName("SUGGESTIONS_CHANNEL_ID")
    val suggestionsChannel: Long = 0,
    @SerialName("STATUS_CHANNEL_ID")
    val statusChannel: Long = 0,
    @SerialName("INFO_CHANNEL_ID")
    val infoChannel: Long = 0,
    @SerialName("RULES_CHANNEL_ID")
    val rulesChannel: Long = 0,
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