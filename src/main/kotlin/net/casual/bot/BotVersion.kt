package net.casual.bot

import java.util.Properties

object BotVersion {
    val version: String = read()

    private fun read(): String {
        val version = runCatching {
            BotVersion::class.java.classLoader.getResourceAsStream("bot.properties")?.use { stream ->
                val properties = Properties()
                properties.load(stream)
                properties.getProperty("version")
            }
        }.getOrNull()
        return version?.takeIf { it.isNotBlank() } ?: "unknown"
    }
}
