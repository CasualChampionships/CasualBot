package commands

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

abstract class AbstractCommand {
    abstract fun getName(): String

    abstract fun getDescription(): String

    abstract fun buildCommand(command: SlashCommandData)

    abstract fun onCommand(event: GenericCommandInteractionEvent)
}