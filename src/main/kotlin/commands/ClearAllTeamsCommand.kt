package commands

import BOT
import dev.minn.jda.ktx.interactions.commands.restrict
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import util.CommandUtil

class ClearAllTeamsCommand: AbstractCommand() {
    override fun getName() = "clearallteams"

    override fun getDescription() = "Clears all the UHC teams"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        if (!CommandUtil.isMemberAdmin(event)) {
            println("NOT ADMIN")
            return
        }

        BOT.db.clearAllTeams()
        event.reply("Successfully cleared all teams").queue()
    }
}