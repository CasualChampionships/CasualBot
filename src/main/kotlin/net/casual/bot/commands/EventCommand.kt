package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.casual.bot.CasualBot
import net.casual.bot.database.EventMode
import net.casual.bot.database.EventState
import net.casual.bot.event.EventService
import net.casual.bot.panel.RegistrationPanel
import net.casual.bot.utils.CommandUtils
import net.casual.bot.utils.CommandUtils.isOrganizer
import net.casual.bot.utils.EventEmbeds
import net.casual.bot.utils.impl.LoadingMessage
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

object EventCommand: Command {
    override val name = "event"
    override val description = "Set up and run an event"

    override fun build(command: SlashCommandData) {
        command.subcommand("create", "Start a new event") {
            option<String>("name", "The event name", true)
            option<String>("mode", "How players get their teams", true) {
                choice("Servers", EventMode.Servers.name)
                choice("Randomized", EventMode.Randomized.name)
                choice("Manual", EventMode.Manual.name)
            }
            option<Int>("team-size", "How many players per team", true)
        }
        command.subcommand("register", "Register someone else for the event") {
            CommandUtils.addPlayerOption(this)
            CommandUtils.addTeamOption(this, false)
            CommandUtils.addUserOption(this)
        }
        command.subcommand("unregister", "Take someone else out of the event") {
            CommandUtils.addPlayerOption(this)
        }
        command.subcommand("status", "Show the current event")
        command.subcommand("open", "Open registration")
        command.subcommand("close", "Close registration")
        command.subcommand("allocate", "Draw random teams") {
            option<Boolean>("preview", "Show the draw without saving it. Defaults to true")
        }
        command.subcommand("panel", "Post the registration panel in this channel")
        command.subcommand("end", "Finish the event")
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        if (!command.isOrganizer()) {
            loading.replace(EventEmbeds.organizersOnly())
            return
        }

        when (command.subcommandName) {
            "create" -> this.createEvent(command, loading)
            "register" -> this.registerOther(command, loading)
            "unregister" -> this.unregisterOther(command, loading)
            "status" -> this.showStatus(loading)
            "open" -> this.changeState(EventState.Open, loading)
            "close" -> this.changeState(EventState.Closed, loading)
            "allocate" -> this.allocate(command, loading)
            "panel" -> this.postPanel(command, loading)
            "end" -> this.endEvent(command, loading)
        }
    }

    private suspend fun createEvent(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val teamSize = command.getOption<Int>("team-size")!!
        if (teamSize < 1) {
            loading.replace(
                EventEmbeds.failure("Team size must be at least 1", "Pick a number of players per team")
            )
            return
        }

        val mode = EventMode.valueOf(command.getOption<String>("mode")!!)
        val name = command.getOption<String>("name")!!
        val previous = EventService.activeEvent()
        val event = EventService.createEvent(name, mode, teamSize)

        loading.replace(
            EventEmbeds.notice(
                "Created ${EventEmbeds.escape(event.name)}",
                buildString {
                    append("${EventEmbeds.modeLabel(mode)}, teams of **$teamSize**.\n")
                    if (previous != null) {
                        append("**${EventEmbeds.escape(previous.name)}** has been archived.\n")
                    }
                    append("\nRegistration is closed. Use `/event open` when you're ready, ")
                    append("then `/event panel` to post the sign-up panel")
                    if (mode == EventMode.Manual) {
                        append("\nPlayers pick their team with `/team join` once they've registered")
                    }
                }
            )
        )
    }

    override suspend fun autocomplete(event: CommandAutoCompleteInteractionEvent) {
        CommandUtils.completeTeams(event)
    }

    private suspend fun registerOther(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val player = CommandUtils.getPlayer(command, loading) ?: return

        val teamName = command.getOption<String>("team")
        val team = teamName?.let { CasualBot.database.getDiscordTeam(it) }
        if (teamName != null && team == null) {
            loading.replace(EventEmbeds.unknownTeam(teamName))
            return
        }

        val user = command.getOption<User>("user")
        val event = EventService.latestEvent()
        loading.replace(
            EventEmbeds.add(EventService.addPlayer(player, team, user?.idLong, force = true))
        )
        event?.let { RegistrationPanel.refresh(it) }
    }

    private suspend fun unregisterOther(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val player = CommandUtils.getPlayer(command, loading) ?: return
        loading.replace(EventEmbeds.remove(EventService.removePlayer(player, force = true)))
        EventService.latestEvent()?.let { RegistrationPanel.refresh(it) }
    }

    private suspend fun showStatus(loading: LoadingMessage) {
        val event = EventService.activeEvent()
        if (event == null) {
            loading.replace(EventEmbeds.noActiveEvent())
            return
        }
        val summary = EventService.summarise(event)
        val drift = if (event.mode == EventMode.Servers) {
            CommandUtils.roleHolders()?.let { EventService.drift(event, it) } ?: listOf()
        } else {
            listOf()
        }
        loading.replace(EventEmbeds.status(summary, drift))
    }

    private suspend fun changeState(state: EventState, loading: LoadingMessage) {
        val event = EventService.activeEvent()
        if (event == null) {
            loading.replace(EventEmbeds.noActiveEvent())
            return
        }
        EventService.setState(event, state)
        RegistrationPanel.refresh(event)

        val embed = if (state == EventState.Open) {
            EventEmbeds.notice("Registration is open", "Players can sign up now")
        } else {
            EventEmbeds.notice("Registration is closed", "Nobody new can sign up")
        }
        loading.replace(embed)
    }

    private suspend fun allocate(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val preview = command.getOption<Boolean>("preview") ?: true
        val result = EventService.allocate(apply = !preview)

        if (preview) {
            loading.replace(
                content = null,
                embeds = listOf(EventEmbeds.allocate(result, applied = false)),
                components = listOf(
                    ConfirmComponents.buttons(
                        ConfirmComponents.EVENT_ALLOCATE,
                        "draw",
                        command.user.idLong,
                        confirmLabel = "Save these teams",
                        danger = false,
                        retryLabel = "Reshuffle"
                    )
                )
            )
            return
        }

        val event = EventService.activeEvent()
        if (event != null) {
            RegistrationPanel.refresh(event)
        }
        loading.replace(EventEmbeds.allocate(result, applied = true))
    }

    private suspend fun postPanel(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val event = EventService.activeEvent()
        if (event == null) {
            loading.replace(EventEmbeds.noActiveEvent())
            return
        }

        val channel = command.channel as? GuildMessageChannel
        if (channel == null) {
            loading.replace(
                EventEmbeds.failure("This isn't a server channel", "Run this in the channel you want the panel in")
            )
            return
        }

        RegistrationPanel.post(channel, event)
        loading.replace(
            EventEmbeds.notice("Panel posted", "It updates itself as people register")
        )
    }

    private suspend fun endEvent(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val event = EventService.activeEvent()
        if (event == null) {
            loading.replace(EventEmbeds.noActiveEvent())
            return
        }

        loading.replace(
            content = null,
            embeds = listOf(
                EventEmbeds.notice(
                    "Finish ${EventEmbeds.escape(event.name)}?",
                    "Registrations are kept, but nobody will be able to join and the panel stops working"
                )
            ),
            components = listOf(
                ConfirmComponents.buttons(
                    ConfirmComponents.EVENT_END,
                    event.id.value.toString(),
                    command.user.idLong,
                    confirmLabel = "Finish event"
                )
            )
        )
    }
}
