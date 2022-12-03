package event

import BOT
import CONFIG
import LOGGER
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import util.CommandUtil
import util.EmbedUtil

class EventHandler: ListenerAdapter() {
    override fun onReady(event: ReadyEvent) {
        LOGGER.info("UHC Bot Started!")
        CommandUtil.loadCommands(event.jda)
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.channel.idLong == CONFIG.suggestionsId) {
            val message = event.message
            message.createThreadChannel(message.contentRaw)
            event.channel.iterableHistory.find { it.author == BOT.jda.selfUser }?.delete()?.queue()
            // event.channel.sendMessageEmbeds(EmbedUtil)
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        CommandUtil.onCommand(event)
    }
}