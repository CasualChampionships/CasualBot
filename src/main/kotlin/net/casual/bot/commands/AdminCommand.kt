package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.casual.bot.CasualBot
import net.casual.bot.utils.CommandUtils.isOrganizer
import net.casual.bot.utils.EventEmbeds
import net.casual.bot.utils.impl.LoadingMessage
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

object AdminCommand : Command {
    override val name = "admin"

    override val description = "Commands to manage admin stuff"

    override fun build(command: SlashCommandData) {
        command.subcommand("dev", "Mark the event as dev") {
            option<Boolean>("is-dev", "Whether to mark as dev", true)
        }
        command.subcommand("twisted", "Change whether the event is twisted") {
            option<Boolean>("is-twisted", "Whether the event should be twisted", true)
        }
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        if (!command.isOrganizer()) {
            loading.replace(EventEmbeds.organizersOnly())
            return
        }

        when (command.subcommandName) {
            "dev" -> this.setDevMode(command, loading)
            "twisted" -> this.setTwistedMode(command, loading)
        }
    }

    private suspend fun setDevMode(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val dev = command.getOption<Boolean>("is-dev")!!
        CasualBot.modifyState(dev = dev)
        loading.replace("Dev set to: $dev")
    }

    private suspend fun setTwistedMode(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val twisted = command.getOption<Boolean>("is-twisted")!!
        CasualBot.modifyState(twisted = twisted)
        loading.replace("Twisted set to: $twisted")
    }
}
