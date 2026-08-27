package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.casual.bot.CasualBot
import net.casual.bot.database.BotDatabase.registrationOf
import net.casual.bot.database.BotEvent
import net.casual.bot.database.Registration
import net.casual.bot.database.BotRegistrations
import net.casual.bot.event.EventService
import net.casual.bot.panel.RegistrationPanel
import net.casual.bot.utils.CommandUtils
import net.casual.bot.utils.CommandUtils.canModifyTeam
import net.casual.bot.utils.CommandUtils.isOrganizer
import net.casual.bot.utils.EventEmbeds
import net.casual.bot.utils.impl.LoadingMessage
import net.casual.bot.utils.parseHexColour
import net.casual.database.DiscordTeam
import dev.minn.jda.ktx.messages.Embed
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

object TeamCommand: Command {
    private const val COLOUR_EXAMPLE = "#55FFFF"

    private val organizerOnly = setOf("create", "edit", "delete", "sync")

    override val name = "team"
    override val description = "Create and manage teams"

    override fun build(command: SlashCommandData) {
        command.subcommand("create", "Create a new team") {
            option<String>("name", "The team name", true)
            addDetailOptions()
        }
        command.subcommand("edit", "Change a team's details") {
            CommandUtils.addTeamOption(this)
            option<String>("name", "A new team name")
            addDetailOptions()
        }
        command.subcommand("delete", "Delete a team") {
            CommandUtils.addTeamOption(this)
        }
        command.subcommand("join", "Put yourself on a team") {
            CommandUtils.addTeamOption(this)
        }
        command.subcommand("add", "Add a player to a team") {
            CommandUtils.addTeamOption(this)
            CommandUtils.addPlayerOption(this)
            CommandUtils.addUserOption(this)
        }
        command.subcommand("remove", "Remove a player from their team") {
            CommandUtils.addPlayerOption(this)
        }
        command.subcommand("clear", "Remove every player from a team") {
            CommandUtils.addTeamOption(this)
        }
        command.subcommand("info", "Show a team's roster") {
            CommandUtils.addTeamOption(this)
        }
        command.subcommand("sync", "Compare team rosters against Discord roles")
    }

    private fun SubcommandData.addDetailOptions() {
        option<Role>("role", "The Discord role that decides who is on this team")
        option<TextChannel>("channel", "The team's channel")
        option<String>("color", "Hex colour, for example $COLOUR_EXAMPLE")
        option<String>("logo", "URL of the team logo")
    }

    override suspend fun autocomplete(event: CommandAutoCompleteInteractionEvent) {
        CommandUtils.completeTeams(event)
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        if (command.subcommandName in this.organizerOnly && !command.isOrganizer()) {
            loading.replace(EventEmbeds.organizersOnly())
            return
        }

        when (command.subcommandName) {
            "create" -> this.createTeam(command, loading)
            "edit" -> this.editTeam(command, loading)
            "delete" -> this.deleteTeam(command, loading)
            "join" -> this.joinTeam(command, loading)
            "add" -> this.addPlayer(command, loading)
            "remove" -> this.removePlayer(command, loading)
            "clear" -> this.clearTeam(command, loading)
            "info" -> this.teamInfo(command, loading)
            "sync" -> this.syncRoles(command, loading)
        }
    }

    private suspend fun createTeam(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val teamName = event.getOption<String>("name")!!
        if (CasualBot.database.getDiscordTeam(teamName) != null) {
            loading.replace(EventEmbeds.duplicateTeam(teamName))
            return
        }

        val rawColour = event.getOption<String>("color")
        val colour = parseHexColour(rawColour)
        if (rawColour != null && colour == null) {
            loading.replace(EventEmbeds.badColour(COLOUR_EXAMPLE))
            return
        }

        val role = event.getOption<Role>("role")
        val channel = event.getOption<TextChannel>("channel")

        val team = CasualBot.database.transaction {
            DiscordTeam.new {
                this.name = teamName
                this.prefix = teamName
                this.logo = event.getOption<String>("logo")
                this.color = colour ?: 0xFFFFFF
                this.wins = 0
                this.roleId = role?.idLong
                this.channelId = channel?.idLong
            }
        }

        loading.replace(this.teamEmbed(team, "Created ${team.name}"))
    }

    private suspend fun editTeam(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EventEmbeds.unknownTeam(name))
            return
        }

        val rawColour = event.getOption<String>("color")
        val colour = parseHexColour(rawColour)
        if (rawColour != null && colour == null) {
            loading.replace(EventEmbeds.badColour(COLOUR_EXAMPLE))
            return
        }

        val newName = event.getOption<String>("name")
        if (newName != null && !newName.equals(team.name, true)) {
            if (CasualBot.database.getDiscordTeam(newName) != null) {
                loading.replace(EventEmbeds.duplicateTeam(newName))
                return
            }
        }

        CasualBot.database.transaction {
            newName?.let { team.name = it }
            colour?.let { team.color = it }
            event.getOption<String>("logo")?.let { team.logo = it }
            event.getOption<Role>("role")?.let { team.roleId = it.idLong }
            event.getOption<TextChannel>("channel")?.let { team.channelId = it.idLong }
        }

        loading.replace(this.teamEmbed(team, "Updated ${team.name}"))
    }

    private suspend fun deleteTeam(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EventEmbeds.unknownTeam(name))
            return
        }

        val members = CasualBot.database.transaction { team.players.count() }
        loading.replace(
            content = null,
            embeds = listOf(
                EventEmbeds.notice(
                    "Delete ${EventEmbeds.escape(team.name)}?",
                    if (members > 0) {
                        "$members player(s) are on this team. Deleting it clears their registrations for it."
                    } else {
                        "This team has no players on it."
                    }
                )
            ),
            components = listOf(
                ConfirmComponents.buttons(
                    ConfirmComponents.TEAM_DELETE,
                    team.id.value.toString(),
                    event.user.idLong,
                    confirmLabel = "Delete"
                )
            )
        )
    }

    private suspend fun joinTeam(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EventEmbeds.unknownTeam(name))
            return
        }

        val result = EventService.join(event.user.idLong, team)
        loading.replace(EventEmbeds.join(result))
        EventService.activeEvent()?.let { RegistrationPanel.refresh(it) }
    }

    private suspend fun addPlayer(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EventEmbeds.unknownTeam(name))
            return
        }

        val activeEvent = this.requireActiveEvent(loading) ?: return
        if (!event.canModifyTeam(team)) {
            loading.replace(this.notOnTeam(team))
            return
        }

        val player = CommandUtils.getPlayer(event, loading) ?: return
        val user = event.getOption<User>("user")
        loading.replace(EventEmbeds.add(EventService.addPlayer(player, team, user?.idLong)))
        RegistrationPanel.refresh(activeEvent)
    }

    private suspend fun removePlayer(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val player = CommandUtils.getPlayer(event, loading) ?: return
        val activeEvent = this.requireActiveEvent(loading) ?: return

        val registration = CasualBot.database.registrationOf(activeEvent, player)
        val team = registration?.let { CasualBot.database.transaction { it.team } }
        if (team != null && !event.canModifyTeam(team)) {
            loading.replace(this.notOnTeam(team))
            return
        }
        if (team == null && !event.isOrganizer()) {
            loading.replace(EventEmbeds.organizersOnly())
            return
        }

        loading.replace(EventEmbeds.remove(EventService.removePlayer(player)))
        RegistrationPanel.refresh(activeEvent)
    }

    private suspend fun clearTeam(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EventEmbeds.unknownTeam(name))
            return
        }

        val activeEvent = this.requireActiveEvent(loading) ?: return
        if (!event.canModifyTeam(team)) {
            loading.replace(this.notOnTeam(team))
            return
        }

        val cleared = CasualBot.database.transaction {
            val registrations = Registration.find {
                (BotRegistrations.event eq activeEvent.id) and (BotRegistrations.team eq team.id)
            }.toList()
            registrations.forEach { it.delete() }
            registrations.size
        }

        loading.replace(
            EventEmbeds.notice(
                "Cleared ${EventEmbeds.escape(team.name)}",
                "$cleared registration(s) removed"
            )
        )
        RegistrationPanel.refresh(activeEvent)
    }

    private suspend fun teamInfo(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EventEmbeds.unknownTeam(name))
            return
        }
        loading.replace(this.teamEmbed(team, "Info for ${team.name}"))
    }

    private suspend fun syncRoles(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val activeEvent = this.requireActiveEvent(loading) ?: return

        val holders = CommandUtils.roleHolders()
        if (holders == null) {
            loading.replace(this.membersUnavailable())
            return
        }

        val drift = EventService.drift(activeEvent, holders)
        loading.replace(EventEmbeds.drift(activeEvent, drift))
    }

    private suspend fun requireActiveEvent(loading: LoadingMessage): BotEvent? {
        val activeEvent = EventService.activeEvent()
        if (activeEvent == null) {
            loading.replace(EventEmbeds.noActiveEvent())
        }
        return activeEvent
    }

    private fun membersUnavailable(): MessageEmbed {
        return EventEmbeds.failure(
            "Can't read Discord roles", "Tell Sensei, he needs to fix the bot"
        )
    }

    private fun teamEmbed(team: DiscordTeam, heading: String): MessageEmbed {
        val members = EventService.roster(team)
        return Embed {
            title = heading
            color = team.color
            thumbnail = team.logo
            description = buildString {
                if (team.roleId != null) {
                    append("Role: <@&${team.roleId}>\n")
                }
                if (team.channelId != null) {
                    append("Channel: <#${team.channelId}>\n")
                }
                if (members == null) {
                    append("\n*No event is running, so nobody is registered yet.*")
                    return@buildString
                }
                append("\n**Players** (${members.size})\n")
                if (members.isEmpty()) {
                    append("*Nobody is registered for this team yet.*")
                } else {
                    append(members.joinToString("\n") { "- ${EventEmbeds.escape(it)}" })
                }
            }
        }
    }

    private fun notOnTeam(team: DiscordTeam): MessageEmbed {
        if (team.roleId == null) {
            return EventEmbeds.failure(
                "Only organizers can change ${EventEmbeds.escape(team.name)}",
                "That team has no Discord role attached"
            )
        }
        return EventEmbeds.failure(
            "You're not on ${EventEmbeds.escape(team.name)}",
            "You need the <@&${team.roleId}> role to change this team's roster"
        )
    }
}
