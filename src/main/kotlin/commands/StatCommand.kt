package commands

import dev.minn.jda.ktx.interactions.commands.restrict
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import util.CommandUtil.addPlayerArgument

class StatCommand: AbstractCommand() {
    override fun getName() = "stat"

    override fun getDescription() = "Shows a specified player's stats"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
        command.addPlayerArgument()
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {

        TODO("Not yet implemented")
    }
}