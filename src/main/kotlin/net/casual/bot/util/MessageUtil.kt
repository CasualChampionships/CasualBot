package net.casual.bot.util

import net.casual.bot.CasualBot
import net.casual.bot.util.impl.LoadingMessage
import net.casual.bot.util.impl.LoadingMessage.Companion.loading
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import kotlin.math.max

object MessageUtil {
    fun IReplyCallback.loading(): LoadingMessage {
        return reply(CasualBot.config.loadingMessage).loading()
    }

    fun replaceFirstMessages(jda: JDA, channelId: Long, contents: List<String>, embeds: List<MessageEmbed?>) {
        val channel: TextChannel = jda.getTextChannelById(channelId) ?: return

        // Make sure to delete all messages that aren't from the bot. The limit is 100 messages, but it should never reach that
        val messages = channel.history.retrievePast(100).complete()
        for (message in messages) {
            if (message.author.id != jda.selfUser.id) {
                message.delete().queue()
            }
        }

        val totalMessages = max(contents.size, embeds.size)

        // Send new messages or edit existing ones
        for (i in 0 until totalMessages) {
            val content = if (i < contents.size) contents[i] else ""
            val embed = if (i < embeds.size) embeds[i] else null

            if (i < messages.size) {
                // Edit an existing message
                val message = messages[messages.size - 1 - i]
                if (embed != null) {
                    message.editMessage(content).setEmbeds(embed).queue()
                } else {
                    // Make sure the embed is removed if set to null and there was one before
                    message.editMessage(content).setEmbeds().queue()
                }
            } else {
                // Send a new message
                if (embed != null) {
                    channel.sendMessage(content).setEmbeds(embed).queue()
                } else {
                    channel.sendMessage(content).queue()
                }
            }
        }

        // Delete extra messages if any, IDK why its needed, but it fails without it
        if (messages.size > totalMessages) {
            for (i in totalMessages until messages.size) {
                messages[messages.size - 1 - i].delete().queue()
            }
        }
    }
}

