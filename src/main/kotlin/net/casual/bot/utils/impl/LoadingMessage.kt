package net.casual.bot.utils.impl

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.editMessage
import kotlinx.coroutines.future.await
import net.casual.bot.CasualBot
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.AttachedFile
import java.util.concurrent.CompletableFuture

class LoadingMessage(private val future: CompletableFuture<InteractionHook>) {
    var replaced = false
        private set

    suspend fun replace(vararg embeds: MessageEmbed) {
        this.replace(embeds = embeds.toList())
    }

    suspend fun replace(
        content: String? = null,
        embeds: Collection<MessageEmbed>? = null,
        components: Collection<MessageTopLevelComponent>? = null,
        attachments: Collection<AttachedFile>? = null
    ) {
        future.await().editMessage(
            content = content,
            embeds = embeds,
            components = components,
            attachments = attachments,
            replace = true
        ).await()
        this.replaced = true
    }

    suspend fun replaceQuietly(vararg embeds: MessageEmbed) {
        try {
            this.replace(embeds = embeds.toList())
        } catch (e: Exception) {
            CasualBot.logger.error(e) { "Could not update the loading message" }
        }
    }

    companion object {
        fun RestAction<InteractionHook>.loading(): LoadingMessage {
            return LoadingMessage(submit())
        }
    }
}
