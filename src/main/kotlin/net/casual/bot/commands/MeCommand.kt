package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.Embed
import net.casual.bot.CasualBot
import net.casual.bot.database.BotDatabase.linkedPlayer
import net.casual.bot.database.BotDatabase.registrationOf
import net.casual.bot.event.EventService
import net.casual.bot.utils.EventEmbeds
import net.casual.bot.utils.impl.LoadingMessage
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

object MeCommand: Command {
    private const val USERNAME_HINT = "Your Minecraft username, if you haven't linked one yet"

    override val name = "me"
    override val description = "Register for the event and check your status"

    override val ephemeral = true

    override fun build(command: SlashCommandData) {
        command.subcommand("register", "Register for the current event, or stop spectating") {
            option<String>("username", USERNAME_HINT)
        }
        command.subcommand("unregister", "Withdraw from the current event")
        command.subcommand("spectate", "Watch instead of playing") {
            option<String>("username", USERNAME_HINT)
        }
        command.subcommand("status", "Show your Minecraft account, team and status")
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val discordId = command.user.idLong
        val roleIds = command.member?.roles?.map { it.idLong } ?: listOf()
        val username = command.getOption<String>("username")?.trim()?.takeIf { it.isNotEmpty() }

        val embed = when (command.subcommandName) {
            "register" -> EventEmbeds.register(EventService.register(discordId, roleIds, username))
            "unregister" -> EventEmbeds.leave(EventService.leave(discordId))
            "spectate" -> EventEmbeds.spectate(EventService.spectate(discordId, username))
            "status" -> this.status(discordId)
            else -> return
        }

        loading.replace(embed)

        if (command.subcommandName != "status") {
            EventService.activeEvent()?.let { EventService.rosterChanged(it) }
        }
    }

    private fun status(discordId: Long): MessageEmbed {
        val linked = CasualBot.database.linkedPlayer(discordId)
        val event = EventService.activeEvent()
            ?: return EventEmbeds.noActiveEvent()

        val registration = CasualBot.database.registrationOf(event, discordId)
        return Embed {
            title = "Your status"
            color = EventEmbeds.NEUTRAL
            description = buildString {
                append("Event: **${EventEmbeds.escape(event.displayName)}** (${EventEmbeds.stateLabel(event)})\n")
                append("Minecraft account: ")
                append(if (linked == null) "**not linked yet**" else "**${EventEmbeds.escape(linked.name)}**")
                append("\n")

                if (registration == null) {
                    append("You are **not registered**.")
                    return@buildString
                }

                CasualBot.database.transaction {
                    if (registration.spectating) {
                        append("You are **spectating**.")
                        return@transaction
                    }
                    val team = registration.team
                    if (team == null) {
                        append("You are **registered**, waiting for teams to be drawn.")
                    } else {
                        append("You are playing for **${EventEmbeds.escape(team.name)}**.")
                    }
                }
            }
        }
    }
}
