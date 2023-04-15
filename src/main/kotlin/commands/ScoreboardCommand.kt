package commands

import BOT
import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.editMessage
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

class ScoreboardCommand: AbstractCommand() {
    override val name = "scoreboard"

    override fun getDescription() = "Shows the scoreboard for a given stat"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
        command.option<String>("stat", "The stat to display", true) {
            choice("Damage Taken", "damage taken")
            choice("Damage Dealt", "damage dealt")
            choice("Deaths", "deaths")
            choice("Kills", "kills")
        }
        command.option<Boolean>("show_all", "Whether to show all players")
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        val hook = event.reply("Loading scoreboards...").complete()
        val stat = event.getOption<String>("stat") ?: return
        val all = event.getOption<Boolean>("show_all") ?: false
        val (embed, file) = BOT.db.getScoreboard(stat, all)
        hook.editMessage("@original", "", listOf(embed), null, file?.let { listOf(it) }, true).queue()
    }
}