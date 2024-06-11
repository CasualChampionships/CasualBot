package net.casual.bot.config

import dev.minn.jda.ktx.messages.EmbedBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.casual.bot.CasualBot
import net.casual.bot.util.EmbedUtil
import net.dv8tion.jda.api.entities.MessageEmbed
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*

@Serializable
data class DatabaseLogin(
    val url: String = "",
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class EmbedChannels(
    val wins: Long = 0L,
    val suggestions: Long = 0L,
    val status: Long = 0L,
    val info: Long = 0L,
    val rules: Long = 0L
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Config(
    val dev: Boolean = true,
    val token: String = "",
    val loadingMessage: String = "",
    val databaseLogin: DatabaseLogin = DatabaseLogin(),
    val guildId: Long = 0L,
    val channelIds: EmbedChannels = EmbedChannels(),
    private val embeds: List<Embed> = listOf(),
) {
    @Transient
    private val embedsByName = this.embeds.associateBy { it.name }

    fun embedOrDefault(name: String): MessageEmbed {
        val embed = embedsByName[name] ?: return EmbedUtil.somethingWentWrongEmbed("Embed not found!")
        return embed.toMessageEmbed()
    }

    companion object {
        private val path = Path.of("casual_config.json")

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        fun read(): Config {
            try {
                if (this.path.exists()) {
                    return this.path.inputStream().use {
                        json.decodeFromStream(it)
                    }
                }
            } catch (e: Exception) {
                CasualBot.logger.error(e) { "Failed to read config" }
            }
            CasualBot.logger.info { "Generating default config..." }
            return Config().also { this.write(it) }
        }

        private fun write(config: Config) {
            try {
                this.path.outputStream().use {
                    json.encodeToStream(config, it)
                }
            } catch (e: IOException) {
                CasualBot.logger.error(e) { "Failed to write config" }
            } catch (e: SerializationException) {
                CasualBot.logger.error(e) { "Failed to serialize config" }
            }
        }
    }
}

@Serializable
data class Field(
    val title: String,
    val description: List<String>
)

@Serializable
data class Embed(
    val name: String,
    val title: String,
    val fields: List<Field>,
    val accent: Int,
) {
    fun toMessageEmbed(): MessageEmbed {
        return EmbedBuilder {
            title = this@Embed.title

            val iter = fields.iterator()
            while (iter.hasNext()) {
                val (title, description) = iter.next()
                field {
                    name = title
                    value = description.joinToString(" ").trimEnd() + if (iter.hasNext()) "\n\n_ _" else ""
                    inline = false
                }
            }
            color = accent
        }.build()
    }
}