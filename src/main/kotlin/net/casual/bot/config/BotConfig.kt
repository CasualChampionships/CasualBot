package net.casual.bot.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.casual.bot.CasualBot
import java.nio.file.Path
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
    fun load(env: Env, legacy: LegacyConfig) {
        synchronized(this) {
            if (this.path.exists()) {
                try {
                    this.data = this.path.inputStream().use { this.json.decodeFromStream(it) }
                    return
                } catch (e: Exception) {
                    CasualBot.logger.error(e) { "Failed to read $path, falling back to the legacy config" }
                }
            }
            this.data = this.fromLegacy(env, legacy)
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
            CasualBot.logger.error(e) { "Failed to write $path" }
        }
    }

    @Deprecated("Migrating away from LegacyConfig")
    private fun fromLegacy(env: Env, legacy: LegacyConfig): BotConfigData {
        val migrated = BotConfigData()

        migrated.winsChannel = env.winsChannel.takeIf { it != 0L }
        migrated.suggestionsChannel = env.suggestionsChannel.takeIf { it != 0L }
        migrated.statusChannel = env.statusChannel.takeIf { it != 0L }

        val legacyChannels = mapOf(
            "info" to env.infoChannel,
            "faq" to env.infoChannel,
            "rules" to env.rulesChannel
        )
        val ordered = legacy.embeds.sortedBy {
            when (it.name) {
                "info" -> 0
                "faq" -> 1
                else -> 2
            }
        }
        for (group in ordered) {
            migrated.groups.add(
                EmbedGroupData(
                    group.name,
                    group.title,
                    legacyChannels[group.name]?.takeIf { it != 0L },
                    group.embeds.mapTo(ArrayList()) {
                        EmbedBlockData(it.title.take(256), it.description, it.color)
                    },
                    group.images.toMutableList()
                )
            )
        }
        return migrated
    }
}
