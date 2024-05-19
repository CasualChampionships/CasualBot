package util

import BOT
import CONFIG
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel

object MessageUtil {

    private fun firstMessage(jda: JDA, channelId: Long, contents: List<String>, embeds: List<MessageEmbed?>) {
        val channel: TextChannel = jda.getTextChannelById(channelId) ?: return

        // Make sure to delete all messages that aren't from the bot. The limit is 100 messages, but it should never reach that
        val messages = channel.history.retrievePast(100).complete()
        val messagesToDelete = messages.filter { it.author.id != jda.selfUser.id }
        messagesToDelete.forEach { it.delete().queue() }

        val totalMessages = contents.size.coerceAtLeast(embeds.size)

        // Send new messages or edit existing ones
        for (i in 0 until totalMessages) {
            val content = if (i < contents.size) contents[i] else ""
            val embed = if (i < embeds.size) embeds[i] else null

            if (i < messages.size) {
                // Edit existing message
                val message = messages[messages.size - 1 - i]
                if (embed != null) {
                    message.editMessage(content).setEmbeds(embed).queue()
                } else {
                    message.editMessage(content).setEmbeds()
                        .queue() // Make sure the embed is removed if set to null and there was one before
                }
            } else {
                // Send new message
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

    fun sendInfoMessages(jda: JDA) {
        val (infoEmbed, infoChannelId) = EmbedUtil.customEmbed("info")
        val (faqEmbed, faqChannelId) = EmbedUtil.customEmbed("faq")
        val (rulesEmbed, rulesChannelId) = EmbedUtil.customEmbed("rules")

        firstMessage(jda, CONFIG.channelIds.get("wins").asLong, listOf(""), listOf(BOT.db.getTeamStats()))
        firstMessage(jda, rulesChannelId, listOf(""), listOf(rulesEmbed))
        firstMessage(jda, infoChannelId, listOf(""), listOf(infoEmbed, faqEmbed))
    }
}

