package net.casual.bot.util

import dev.minn.jda.ktx.coroutines.await
import net.casual.bot.CasualBot
import net.casual.bot.util.impl.LoadingMessage
import net.casual.bot.util.impl.LoadingMessage.Companion.loading
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData

object MessageUtil {
    fun IReplyCallback.loading(): LoadingMessage {
        return reply(CasualBot.config.loadingMessage).loading()
    }

    suspend fun editLastMessages(jda: JDA, channelId: Long, vararg messages: MessageCreateData) {
        editLastMessages(jda, channelId, messages.toList())
    }

    suspend fun editLastMessages(jda: JDA, channelId: Long, messages: List<MessageCreateData>) {
        val channel: TextChannel = jda.getTextChannelById(channelId) ?: return

        // Make sure to delete all messages that aren't from the bot.
        // The limit is 100 messages, but it should never reach that.
        val channelHistory = channel.history.retrievePast(100).await()
        val botHistory = ArrayList<Message>()
        for (message in channelHistory) {
            if (message.author.id != jda.selfUser.id) {
                message.delete().queue()
            } else {
                botHistory.add(0, message)
            }
        }

        for ((i, data) in messages.withIndex()) {
            val message = botHistory.getOrNull(i)
            if (message != null) {
                message.editMessage(MessageEditData.fromCreateData(data)).queue()
            } else {
                channel.sendMessage(data).queue()
            }
        }
        for (remaining in botHistory.drop(messages.size)) {
            remaining.delete().queue()
        }
    }
}

