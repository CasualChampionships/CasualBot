package commands

import BOT
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.editMessage
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import util.CommandUtil.addPlayerArgument
import util.CommandUtil.getPlayer
import util.EmbedUtil

class StatCommand: AbstractCommand() {
    override fun getName() = "stat"

    override fun getDescription() = "Shows a specified player's stats"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
        command.addPlayerArgument()
        command.option<Boolean>("lifetime", "Whether to display lifetime stats", false)
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        val username = event.getPlayer {
            event.replyEmbeds(EmbedUtil.somethingWentWrongEmbed("$it is not a valid username")).queue()
        } ?: return

        val lifetime = event.getOption<Boolean>("lifetime") ?: false
        val hook = event.reply("Loading stats...").complete()
        val (embeds, files) = BOT.db.getPlayerStats(username, lifetime)
        hook.editMessage("@original", "", embeds, null, files, true).queue()
    }
}