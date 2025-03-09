package net.casual.bot.config

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.casual.bot.CasualBot
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@Serializable
data class DatabaseLogin(
    val name: String = "",
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
data class Embeds(
    val name: String,
    val title: String = "",
    val images: List<String> = listOf(),
    val embeds: List<Embed> = listOf()
) {
    @Transient
    private val files = ArrayList<Deferred<FileUpload>>()

    init {
        for (image in images) {
            files.add(CasualBot.coroutineScope.async {
                FileUpload.fromData(
                    CasualBot.httpClient.get(image).bodyAsChannel().toInputStream(),
                    image.substringAfterLast('/')
                )
            })
        }
    }

    fun asMessageCreateData(): List<MessageCreateData> {
        val data = ArrayList<MessageCreateData>()
        runBlocking {
            val msg = MessageCreateBuilder()
                .setContent(title)
                .setFiles(files.awaitAll())
                .setEmbeds(embeds.map(Embed::toMessageEmbed)).build()
            data.add(msg)
        }
        return data
    }
}

@Serializable
data class Embed(
    val title: String,
    val description: String,
    val color: Int,
) {
    fun toMessageEmbed(): MessageEmbed {
        return EmbedBuilder()
            .setTitle(title)
            .setDescription(description)
            .setColor(color)
            .build()
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Config(
    val dev: Boolean = true,
    val token: String = "",
    val loadingMessage: String = "",
    val databaseLogin: DatabaseLogin = DatabaseLogin(),
    val minecraftVersion: String = "",
    val guildId: Long = 0L,
    val channelIds: EmbedChannels = EmbedChannels(),
    private val embeds: List<Embeds> = listOf(),
) {
    @Transient
    private val embedsByName = embeds.associateBy { it.name }

    fun embedsByName(name: String): Embeds? {
        return embedsByName[name]
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