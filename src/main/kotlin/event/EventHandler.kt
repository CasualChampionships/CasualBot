package event

import BOT
import CONFIG
import LOGGER
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.internal.requests.Route.Emojis
import util.CommandUtil
import util.EmbedUtil

class EventHandler: ListenerAdapter() {
    override fun onReady(event: ReadyEvent) {
        LOGGER.info("UHC Bot Started!")
        CommandUtil.loadCommands(event.jda)
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.channel.idLong == CONFIG.suggestionsId && event.message.author != BOT.jda.selfUser) {
            val message = event.message
            message.createThreadChannel(message.contentRaw).queue()
            message.addReaction(Emoji.fromUnicode("\uD83D\uDC4D")).queue()
            message.addReaction(Emoji.fromUnicode("\uD83D\uDC4E")).queue()
            event.channel.iterableHistory.find { it.author == BOT.jda.selfUser }?.delete()?.queue()
            val (embed, _) = EmbedUtil.customEmbed("suggestions")
            event.channel.sendMessageEmbeds(embed).queue()
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        CommandUtil.onCommand(event)
    }
}