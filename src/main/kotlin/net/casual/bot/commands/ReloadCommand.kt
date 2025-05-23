package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.restrict
import net.casual.bot.CasualBot
import net.casual.bot.util.CommandUtils.isAdministrator
import net.casual.bot.util.EmbedUtil
import net.casual.bot.util.TwistedUtils
import net.casual.bot.util.impl.LoadingMessage
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

object ReloadCommand: Command {
    override val name = "reload"
    override val description = "Reloads some of the bot"

    override fun build(command: SlashCommandData) {
        command.restrict(true)
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        if (!command.isAdministrator()) {
            loading.replace(EmbedUtil.noPermission()).queue()
            return
        }

        CasualBot.reloadConfig()
        CasualBot.reloadEmbeds()

        if (TwistedUtils.isTwistedDatabase(CasualBot.config.databaseLogin.name)) {
            return loading.replace("Successfully reloaded! \nBot is current in **Twisted** Mode, some features might be disabled!").queue()
        }

        loading.replace("Successfully reloaded!").queue()
    }
}