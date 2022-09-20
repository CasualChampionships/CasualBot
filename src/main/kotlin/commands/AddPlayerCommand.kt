package commands

import BOT
import dev.minn.jda.ktx.interactions.commands.restrict
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import util.CommandUtil
import util.CommandUtil.addPlayerArgument
import util.CommandUtil.addServerArgument
import util.CommandUtil.getPlayer
import util.CommandUtil.getServer
import util.CommandUtil.isServerRole

class AddPlayerCommand: AbstractCommand() {
    override fun getName() = "addplayer"

    override fun getDescription() = "Adds a player to the specified team"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
        command.addServerArgument()
        command.addPlayerArgument()
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        val server = event.getServer {
            event.reply("$it could not be found?!").queue()
        } ?: return
        if (!server.isServerRole()) {
            event.reply("${server.name} is not a valid server!").queue()
            return
        }

        if (!CommandUtil.canMemberModifyTeam(event, server)) {
            return
        }

        val username = event.getPlayer {
            event.reply("$it is not a valid username").queue()
        } ?: return

        event.replyEmbeds(BOT.db.addPlayer(server, username)).queue()
    }
}