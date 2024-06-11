package net.casual.bot.util.impl

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.editMessage
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.LayoutComponent
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.utils.AttachedFile
import java.util.concurrent.CompletableFuture

class LoadingMessage(private val future: CompletableFuture<InteractionHook>) {
    var hasReplaced = false
        private set

    suspend fun replace(vararg embeds: MessageEmbed): WebhookMessageEditAction<Message> {
        return this.replace(embeds = embeds.toList())
    }

    suspend fun replace(
        content: String? = null,
        embeds: Collection<MessageEmbed>? = null,
        components: Collection<LayoutComponent>? = null,
        attachments: Collection<AttachedFile>? = null
    ): WebhookMessageEditAction<Message> {
        this.hasReplaced = true
        return future.await().editMessage(
            content = content,
            embeds = embeds,
            components = components,
            attachments = attachments,
            replace = true
        )
    }

    companion object {
        fun RestAction<InteractionHook>.loading(): LoadingMessage {
            return LoadingMessage(this.submit())
        }
    }
}