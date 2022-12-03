package commands

import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import util.CommandUtil
import util.EmbedUtil

class EmbedCommand: AbstractCommand() {
    override fun getName() = "embed"

    override fun getDescription() = "Gets the bot to post an embed"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
        command.option<String>("name", "The name of the embed", true)
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        if (!CommandUtil.isMemberAdmin(event)) {
            return
        }

        val embedName = event.getOption<String>("name") ?: return
        event.reply("Embed found!").setEphemeral(true).queue()
        event.messageChannel.sendMessageEmbeds(EmbedUtil.configEmbed(embedName)).queue()
    }
}