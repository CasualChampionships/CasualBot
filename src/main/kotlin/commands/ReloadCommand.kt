package commands

import CONFIG
import dev.minn.jda.ktx.interactions.commands.restrict
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

class ReloadCommand: AbstractCommand() {
    override fun getName() = "reload"

    override fun getDescription() = "Reloads some of the bot"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        CONFIG.updateEmbeds()
        event.reply("Successfully reloaded!").queue()
    }
}