package commands

import BOT
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import util.CommandUtil
import util.EmbedUtil

class EmbedCommand: AbstractCommand() {
    override fun getName() = "embed"

    override fun getDescription() = "Gets the bot to post an embed"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true)
        command.option<String>("name", "The name of the embed", true)
        command.option<Boolean>("here", "Whether the embed should be posted here", false)
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        if (!CommandUtil.isMemberAdmin(event)) {
            return
        }

        val embedName = event.getOption<String>("name") ?: return
        event.reply("Embed found!").setEphemeral(true).queue()

        val embedHere = event.getOption<Boolean>("here") ?: false

        val (embed, messageId, channelId) = EmbedUtil.customEmbed(embedName)
        if (messageId != -1L && channelId != -1L && !embedHere) {
            val channel = BOT.guild.getChannel<MessageChannel>(channelId)!!
            channel.retrieveMessageById(messageId).complete().editMessageEmbeds(embed).queue()
            return
        }
        event.messageChannel.sendMessageEmbeds(embed).queue()
    }
}