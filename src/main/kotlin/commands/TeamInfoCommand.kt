package commands

import BOT
import dev.minn.jda.ktx.interactions.commands.restrict
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import util.CommandUtil.addServerArgument
import util.CommandUtil.getServer
import util.CommandUtil.isServerRole

class TeamInfoCommand: AbstractCommand() {
    override fun getName() = "teaminfo"

    override fun getDescription() = "Get a team's information"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
        command.addServerArgument()
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        val (name, role) = event.getServer {
            event.reply("$it could not be found?!").queue()
            return
        }

        event.replyEmbeds(BOT.db.getTeamInfo(name, role.colorRaw)).queue()
    }
}