package net.casual.bot.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.casual.bot.CasualBot
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream


@Deprecated("Migrating away from LegacyConfig")
@Serializable
data class LegacyEmbeds(
    val name: String,
    val title: String = "",
    val images: List<String> = listOf(),
    val embeds: List<LegacyEmbed> = listOf()
)


@Deprecated("Migrating away from LegacyConfig")
@Serializable
data class LegacyEmbed(
    val title: String,
    val description: String,
    val color: Int
)


@Deprecated("Migrating away from LegacyConfig")
@Serializable
data class LegacyConfig(
    val dev: Boolean = true,
    val twisted: Boolean = false,
    val embeds: List<LegacyEmbeds> = listOf()
) {
    companion object {
        private val path = Path.of("casual_config.json")

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun read(): LegacyConfig {
            try {
                if (path.exists()) {
                    return path.inputStream().use { json.decodeFromStream(it) }
                }
            } catch (e: Exception) {
                CasualBot.logger.error(e) { "Failed to read the legacy config" }
            }
            return LegacyConfig()
        }
    }
}
