package commands

import CONFIG
import dev.minn.jda.ktx.interactions.commands.restrict
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import util.CommandUtil
import util.EmbedUtil
import util.ImageUtil

class ReloadCommand: AbstractCommand() {
    override val name = "reload"

    override fun getDescription() = "Reloads some of the bot"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        if (CommandUtil.isMemberAdmin(event)) {
            event.replyEmbeds(EmbedUtil.noPermission()).queue()
            return
        }
        CONFIG.updateEmbeds()
        ImageUtil.clearCaches()
        event.reply("Successfully reloaded!").queue()
    }
}