package net.casual.bot.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.casual.bot.CasualBot
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.io.path.outputStream

@Serializable
data class EmbedBlockData(
    var title: String = "",
    var description: String = "",
    var color: Int = 0xAA9BFF
)

@Serializable
data class EmbedGroupData(
    var name: String,
    var title: String = "",
    var channelId: Long? = null,
    val blocks: MutableList<EmbedBlockData> = ArrayList(),
    val images: MutableList<String> = ArrayList()
)

@Serializable
data class BotConfigData(
    var winsChannel: Long? = null,
    var suggestionsChannel: Long? = null,
    var statusChannel: Long? = null,
    var spectatorTeam: String = "Spectator",
    val groups: MutableList<EmbedGroupData> = ArrayList()
) {
    fun channels(): List<Pair<String, Long?>> {
        return listOf(
            "Team wins" to this.winsChannel,
            "Suggestions" to this.suggestionsChannel,
            "Event status" to this.statusChannel
        )
    }
}

object BotConfig {
    private val path = Path.of("bot-config.json")

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private var data = BotConfigData()

    @OptIn(ExperimentalSerializationApi::class)
    fun load() {
        synchronized(this) {
            if (this.path.exists()) {
                try {
                    this.data = this.path.inputStream().use { this.json.decodeFromStream(it) }
                    CasualBot.logger.info { "Loaded config from ${this.path.absolutePathString()}" }
                } catch (e: Exception) {
                    this.data = BotConfigData()
                    CasualBot.logger.error(e) {
                        "Failed to read ${this.path.absolutePathString()}, using an empty config"
                    }
                }
                return
            }

            this.data = BotConfigData()
            CasualBot.logger.warn { "No config at ${this.path.absolutePathString()}, creating an empty one" }
            this.write()
        }
    }

    fun <T> read(block: BotConfigData.() -> T): T {
        return synchronized(this) { this.data.block() }
    }

    fun <T> update(block: BotConfigData.() -> T): T {
        return synchronized(this) {
            val result = this.data.block()
            this.write()
            result
        }
    }

    fun replace(replacement: BotConfigData) {
        synchronized(this) {
            this.data = replacement
            this.write()
        }
    }

    fun serialize(): String {
        return synchronized(this) { this.json.encodeToString(this.data) }
    }

    fun parse(raw: String): Result<BotConfigData> {
        return runCatching { this.json.decodeFromString<BotConfigData>(raw) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun write() {
        try {
            val temporary = this.path.resolveSibling("${this.path.fileName}.tmp")
            temporary.outputStream().use { this.json.encodeToStream(this.data, it) }
            temporary.moveTo(this.path, overwrite = true)
        } catch (e: Exception) {
            CasualBot.logger.error(e) { "Failed to write ${this.path.absolutePathString()}" }
        }
    }
}
