package event

import LOGGER
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import util.CommandUtil

class EventHandler: ListenerAdapter() {
    override fun onReady(event: ReadyEvent) {
        LOGGER.info("UHC Bot Started!")
        CommandUtil.loadCommands(event.jda)
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        CommandUtil.onCommand(event)
    }
}