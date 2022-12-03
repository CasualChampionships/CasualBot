package commands

import BOT
import dev.minn.jda.ktx.interactions.commands.restrict
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import util.CommandUtil
import util.EmbedUtil

class WinsCommand: AbstractCommand() {
    override fun getName() = "wins"

    override fun getDescription() = "Returns an embed of each the team scoreboard"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        if (!CommandUtil.isMemberAdmin(event)) {
            event.replyEmbeds(EmbedUtil.noPermission()).queue()
            return
        }

        event.reply("Sending wins!").setEphemeral(true).queue()
        event.messageChannel.sendMessageEmbeds(BOT.db.getTeamStats()).queue()
    }
}