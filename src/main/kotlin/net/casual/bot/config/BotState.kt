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
        fun read(legacy: LegacyConfig): BotState {
            if (path.exists()) {
                try {
                    return path.inputStream().use { json.decodeFromStream(it) }
                } catch (e: Exception) {
                    CasualBot.logger.error(e) { "Failed to read bot state, falling back to the config" }
                }
            }
            return BotState(legacy.dev, legacy.twisted).also { write(it) }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun write(state: BotState) {
            try {
                path.outputStream().use { json.encodeToStream(state, it) }
            } catch (e: Exception) {
                CasualBot.logger.error(e) { "Failed to write bot state" }
            }
        }
    }
}
