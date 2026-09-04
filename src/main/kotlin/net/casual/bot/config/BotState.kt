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
data class BotState(
    val dev: Boolean = true,
    val twisted: Boolean = false
) {
    companion object {
        private val path = Path.of("bot-state.json")

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun read(): BotState {
            if (this.path.exists()) {
                try {
                    val state = this.path.inputStream().use { this.json.decodeFromStream<BotState>(it) }
                    CasualBot.logger.info { "Loaded $state from ${this.path.absolutePathString()}" }
                    return state
                } catch (e: Exception) {
                    val fallback = BotState()
                    CasualBot.logger.error(e) {
                        "Failed to read ${this.path.absolutePathString()}, starting from $fallback"
                    }
                    return fallback
                }
            }

            val state = BotState()
            CasualBot.logger.warn { "No state at ${this.path.absolutePathString()}, creating it as $state" }
            this.write(state)
            return state
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun write(state: BotState): Boolean {
            try {
                val temporary = this.path.resolveSibling("${this.path.fileName}.tmp")
                temporary.outputStream().use { this.json.encodeToStream(state, it) }
                temporary.moveTo(this.path, overwrite = true)
                CasualBot.logger.info { "Wrote $state to ${this.path.absolutePathString()}" }
                return true
            } catch (e: Exception) {
                CasualBot.logger.error(e) { "Failed to write state to ${this.path.absolutePathString()}" }
                return false
            }
        }
    }
}
