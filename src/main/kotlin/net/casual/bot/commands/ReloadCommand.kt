package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.restrict
import net.casual.bot.CasualBot
import net.casual.bot.utils.CommandUtils.isOrganizer
import net.casual.bot.utils.EventEmbeds
import net.casual.bot.utils.impl.LoadingMessage
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

object ReloadCommand: Command {
    override val name = "reload"
    override val description = "Reloads some of the bot"

    override fun build(command: SlashCommandData) {
        command.restrict(true)
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        if (!command.isOrganizer()) {
            loading.replace(EventEmbeds.organizersOnly())
            return
        }

        CasualBot.reloadConfig()
        CasualBot.reloadEmbeds()

        loading.replace(
            EventEmbeds.notice(
                "Reloaded",
                "Settings were re-read and every message group was republished."
            )
        )
    }
}