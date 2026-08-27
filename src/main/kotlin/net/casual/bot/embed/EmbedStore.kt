package net.casual.bot.embed

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import net.casual.bot.CasualBot
import net.casual.bot.config.BotConfig
import net.casual.bot.config.EmbedBlockData
import net.casual.bot.config.EmbedGroupData
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData

object EmbedStore {
    private val extensions = mapOf(
        "image/png" to "png",
        "image/jpeg" to "jpg",
        "image/gif" to "gif",
        "image/webp" to "webp"
    )

    val nameFormat = Regex("[A-Za-z0-9_-]{1,64}")

    fun groups(): List<EmbedGroupData> {
        return BotConfig.read { groups.map { it.snapshot() } }
    }

    fun group(name: String): EmbedGroupData? {
        return BotConfig.read { groups.firstOrNull { it.name.equals(name, true) }?.snapshot() }
    }

    fun exists(name: String): Boolean {
        return BotConfig.read { groups.any { it.name.equals(name, true) } }
    }

    fun createGroup(name: String, title: String, channelId: Long?) {
        BotConfig.update { groups.add(EmbedGroupData(name, title, channelId)) }
    }

    fun deleteGroup(name: String) {
        BotConfig.update { groups.removeIf { it.name.equals(name, true) } }
    }

    fun setTitle(name: String, title: String) {
        this.mutate(name) { it.title = title }
    }

    fun setChannel(name: String, channelId: Long?) {
        this.mutate(name) { it.channelId = channelId }
    }

    fun addBlock(name: String, block: EmbedBlockData) {
        this.mutate(name) { it.blocks.add(block) }
    }

    fun updateBlock(name: String, index: Int, block: EmbedBlockData) {
        this.mutate(name) {
            if (index in it.blocks.indices) {
                it.blocks[index] = block
            }
        }
    }

    fun removeBlock(name: String, index: Int) {
        this.mutate(name) {
            if (index in it.blocks.indices) {
                it.blocks.removeAt(index)
            }
        }
    }

    fun moveBlock(name: String, from: Int, to: Int) {
        this.mutate(name) {
            if (from !in it.blocks.indices) {
                return@mutate
            }
            val block = it.blocks.removeAt(from)
            it.blocks.add(to.coerceIn(0, it.blocks.size), block)
        }
    }

    fun addImage(name: String, url: String) {
        this.mutate(name) { it.images.add(url) }
    }

    fun clearImages(name: String) {
        this.mutate(name) { it.images.clear() }
    }

    fun groupsForChannel(channelId: Long): List<EmbedGroupData> {
        return BotConfig.read { groups.filter { it.channelId == channelId }.map { it.snapshot() } }
    }

    fun channels(): List<Long> {
        return BotConfig.read { groups.mapNotNull { it.channelId }.distinct() }
    }

    suspend fun toMessageData(group: EmbedGroupData): MessageCreateData {
        val builder = MessageCreateBuilder()
        if (group.title.isNotEmpty()) {
            builder.setContent(group.title)
        }
        builder.setEmbeds(group.blocks.map { this.toMessageEmbed(it) })
        builder.setFiles(group.images.mapNotNull { this.download(it) })
        return builder.build()
    }

    fun isEmpty(group: EmbedGroupData): Boolean {
        return group.title.isEmpty() && group.blocks.isEmpty() && group.images.isEmpty()
    }

    suspend fun download(raw: String): FileUpload? {
        val url = raw.trim()
        return try {
            val response = CasualBot.httpClient.get(url)
            if (!response.status.isSuccess()) {
                CasualBot.logger.warn { "Embed image $url returned ${response.status}" }
                return null
            }
            val bytes = response.bodyAsBytes()
            if (bytes.isEmpty()) {
                CasualBot.logger.warn { "Embed image $url was empty" }
                return null
            }
            val type = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()
            FileUpload.fromData(bytes, this.fileName(url, type))
        } catch (e: Exception) {
            CasualBot.logger.warn(e) { "Could not download embed image $url" }
            null
        }
    }

    fun fileName(url: String, contentType: String?): String {
        val raw = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val cleaned = raw.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
        val extension = this.extensions[contentType?.lowercase()]
        if (extension != null) {
            val base = cleaned.substringBeforeLast('.', cleaned).ifEmpty { "image" }
            return "$base.$extension"
        }
        if (cleaned.contains('.') && cleaned.substringAfterLast('.').length in 2..4) {
            return cleaned
        }
        return "image.png"
    }

    private fun toMessageEmbed(block: EmbedBlockData): MessageEmbed {
        return EmbedBuilder()
            .setTitle(block.title.ifEmpty { null })
            .setDescription(block.description)
            .setColor(block.color)
            .build()
    }

    private fun EmbedGroupData.snapshot(): EmbedGroupData {
        return EmbedGroupData(
            name,
            title,
            channelId,
            blocks.mapTo(ArrayList()) { it.copy() },
            ArrayList(images)
        )
    }

    private fun mutate(name: String, block: (EmbedGroupData) -> Unit) {
        BotConfig.update {
            groups.firstOrNull { it.name.equals(name, true) }?.let(block)
        }
    }
}
