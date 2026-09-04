package net.casual.bot.utils

import dev.minn.jda.ktx.messages.Embed
import net.casual.bot.database.BotEvent
import net.casual.bot.database.EventMode
import net.casual.bot.database.EventState
import net.casual.bot.event.AddResult
import net.casual.bot.event.AllocateResult
import net.casual.bot.event.Drift
import net.casual.bot.event.EventSummary
import net.casual.bot.event.JoinResult
import net.casual.bot.event.EventUnavailable
import net.casual.bot.event.LeaveResult
import net.casual.bot.event.RegisterResult
import net.casual.bot.event.RemoveResult
import net.casual.bot.event.SpectateResult
import net.casual.database.DiscordTeam
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.MarkdownSanitizer

object EventEmbeds {
    const val SUCCESS = 0x2F7D5B
    const val NEUTRAL = 0x5A6472
    const val WARNING = 0xB4562A
    const val FAILURE = 0xA33A3A

    private const val STATUS_DRIFT_LIMIT = 20
    private const val DRIFT_LIMIT = 40
    private const val REGISTER_INSTEAD = "Use **Register** if you want to play instead."

    fun escape(text: String): String {
        return MarkdownSanitizer.sanitize(text, MarkdownSanitizer.SanitizationStrategy.ESCAPE)
    }

    fun stateLabel(event: BotEvent): String {
        if (event.archived) {
            return "Finished"
        }
        return when (event.state) {
            EventState.Open -> "Registration open"
            EventState.Closed -> "Registration closed"
        }
    }

    fun modeLabel(mode: EventMode): String {
        return when (mode) {
            EventMode.Servers -> "Teams from your Discord role"
            EventMode.Randomized -> "Teams randomized before the event"
            EventMode.Manual -> "Players pick their own teams"
        }
    }

    fun register(result: RegisterResult): MessageEmbed {
        return when (result) {
            is RegisterResult.Registered -> Embed {
                title = if (result.wasSpectating) {
                    "You're playing, ${escape(result.username)}"
                } else {
                    "You're registered, ${escape(result.username)}"
                }
                color = SUCCESS
                description = buildString {
                    if (result.wasSpectating) {
                        append("You're no longer spectating.\n")
                    }
                    if (result.team != null) {
                        append("You're playing for **${escape(result.team.name)}**.\n")
                    } else {
                        append("You don't have a team yet.\n")
                    }
                    append("Spots remaining: **${result.spotsRemaining}**")
                }
            }
            RegisterResult.AlreadyRegistered -> this.failure(
                "You're already registered",
                "Use **Unregister** if you need to drop out, or **Spectate** to watch instead."
            )
            RegisterResult.NoTeamRole -> this.failure(
                "You don't have a team role yet",
                "Your team comes from your Discord role. Ask an organizer to give you one, then register again."
            )
            is RegisterResult.AmbiguousTeamRoles -> this.failure(
                "You have more than one team role",
                "You hold roles for ${result.teams.joinToString(", ") { "**${this.escape(it)}**" }}, " +
                    "so we can't tell which one you're playing for. " +
                    "Sign up with `/team add` instead and pick the team yourself."
            )
            is RegisterResult.TeamFull -> this.teamFull(result.team, result.teamSize)
            is RegisterResult.UnknownUsername -> this.unknownUsername(result.username)
            is RegisterResult.UsernameTaken -> this.usernameTaken(result.username, result.discordId)
            RegisterResult.EventFull -> this.eventFull()
            RegisterResult.NeedsUsername -> this.needsUsername()
            is EventUnavailable -> this.unavailable(result)
        }
    }

    fun leave(result: LeaveResult): MessageEmbed {
        return when (result) {
            is LeaveResult.Left -> Embed {
                title = "You've been unregistered"
                color = NEUTRAL
                description = "Spots remaining: **${result.spotsRemaining}**"
            }
            LeaveResult.NotRegistered -> this.failure(
                "You aren't registered",
                "There's nothing to unregister."
            )
            is EventUnavailable -> this.unavailable(result)
        }
    }

    fun spectate(result: SpectateResult): MessageEmbed {
        return when (result) {
            is SpectateResult.Spectating -> Embed {
                title = "You're spectating"
                color = NEUTRAL
                description = buildString {
                    if (result.wasPlaying) {
                        append("You've been taken off your team.\n")
                    }
                    append(REGISTER_INSTEAD)
                }
            }
            is SpectateResult.AlreadySpectating -> this.notice(
                "You're already spectating",
                REGISTER_INSTEAD
            )
            SpectateResult.NeedsUsername -> this.needsUsername()
            is SpectateResult.UnknownUsername -> this.unknownUsername(result.username)
            is SpectateResult.UsernameTaken -> this.usernameTaken(result.username, result.discordId)
            is EventUnavailable -> this.unavailable(result)
        }
    }

    fun unavailable(result: EventUnavailable): MessageEmbed {
        return when (result) {
            EventUnavailable.NoActiveEvent -> this.failure(
                "There's no event running",
                "An organizer needs to start one before anyone can register."
            )
            is EventUnavailable.NotAccepting -> when {
                result.archived -> this.failure(
                    "That event has finished",
                    "Watch for the next one to be announced."
                )
                else -> this.failure(
                    "Registration is closed",
                    "An organizer will reopen it before the next event."
                )
            }
        }
    }

    fun noActiveEvent(): MessageEmbed {
        return this.unavailable(EventUnavailable.NoActiveEvent)
    }

    fun panel(summary: EventSummary): MessageEmbed {
        val event = summary.event
        return Embed {
            title = escape(event.name)
            color = if (event.acceptingRegistrations) SUCCESS else NEUTRAL
            description = buildString {
                append(header(event))
                append("Teams of **${event.teamSize}**\n\n")
                append("**${summary.playing}** playing")
                if (summary.spectating > 0) {
                    append(" | **${summary.spectating}** spectating")
                }
                if (event.acceptingRegistrations) {
                    append(" | **${summary.spotsRemaining}** spots left")
                }
                when {
                    summary.unallocated > 0 && event.mode == EventMode.Randomized ->
                        append("\n**${summary.unallocated}** waiting for teams to be drawn")
                    summary.unallocated > 0 && event.mode == EventMode.Manual ->
                        append("\n**${summary.unallocated}** without a team yet")
                }
                if (event.acceptingRegistrations) {
                    when (event.mode) {
                        EventMode.Manual -> append("\n\nRegister, then pick your team with `/team join`.")
                        EventMode.Servers -> append(
                            "\n\nPlaying for more than one server? Sign up with " +
                                "`/team add` and pick the team you're playing for."
                        )
                        EventMode.Randomized -> {}
                    }
                }

                val filled = summary.rosters.filter { it.members.isNotEmpty() }
                if (filled.isNotEmpty()) {
                    append("\n\n")
                    append(filled.joinToString("\n\n") { roster ->
                        "**${escape(roster.team.name)}** (${roster.members.size}/${roster.capacity})\n" +
                            roster.members.joinToString("\n") { "| ${escape(it)}" }
                    })
                }
            }.take(MessageEmbed.DESCRIPTION_MAX_LENGTH)
        }
    }

    fun status(summary: EventSummary, drift: List<Drift>): MessageEmbed {
        val event = summary.event
        return Embed {
            title = escape(event.name)
            color = if (drift.isEmpty()) NEUTRAL else WARNING
            description = buildString {
                append(header(event))
                append("Teams of **${event.teamSize}** | capacity **${summary.capacity}**\n\n")
                append("Playing: **${summary.playing}**\n")
                append("Spectating: **${summary.spectating}**\n")
                append("Without a team: **${summary.unallocated}**\n")
                append("Spots remaining: **${summary.spotsRemaining}**")

                if (drift.isNotEmpty()) {
                    append("\n\n**Roles and rosters disagree**\n")
                    append(listDrift(drift, STATUS_DRIFT_LIMIT))
                }
            }.take(MessageEmbed.DESCRIPTION_MAX_LENGTH)
        }
    }

    fun drift(event: BotEvent, drift: List<Drift>): MessageEmbed {
        if (event.mode != EventMode.Servers) {
            return this.notice(
                "Nothing to compare",
                "This event assigns teams randomly, so Discord roles don't decide who plays for whom."
            )
        }
        if (drift.isEmpty()) {
            return Embed {
                title = "Roles and rosters agree"
                color = SUCCESS
                description = "Everyone registered holds the role for the team they're on."
            }
        }
        return Embed {
            title = "Roles and rosters disagree"
            color = WARNING
            description = listDrift(drift, DRIFT_LIMIT).take(MessageEmbed.DESCRIPTION_MAX_LENGTH)
        }
    }

    fun describe(drift: Drift): String {
        return when (drift) {
            is Drift.OnRosterWithoutRole ->
                "| **${this.escape(drift.username)}** is on ${this.escape(drift.team)} but holds no team role"
            is Drift.RegisteredToWrongTeam ->
                "| **${this.escape(drift.username)}** is on ${this.escape(drift.onRoster)} but holds the ${this.escape(drift.byRole)} role"
            is Drift.HasRoleNotRegistered ->
                "| <@${drift.discordId}> holds the ${this.escape(drift.team)} role but hasn't registered"
            is Drift.NoDiscordAccount ->
                "| **${this.escape(drift.username)}** is on ${this.escape(drift.team)} with no Discord account linked"
        }
    }

    fun allocate(result: AllocateResult, applied: Boolean): MessageEmbed {
        return when (result) {
            is AllocateResult.Allocated -> Embed {
                title = if (applied) "Teams drawn" else "Preview: teams"
                color = if (applied) SUCCESS else NEUTRAL
                description = buildString {
                    append(result.teams.joinToString("\n\n") { allocated ->
                        "**${escape(allocated.team.name)}** (${allocated.players.size})\n" +
                            allocated.players.joinToString("\n") { "| ${escape(it.name)}" }
                    })
                    if (result.unallocated.isNotEmpty()) {
                        append("\n\n**No team (not enough teams)**\n")
                        append(result.unallocated.joinToString("\n") { "| ${escape(it)}" })
                    }
                    if (!applied) {
                        append("\n\nNothing has been saved yet.")
                    }
                }.take(MessageEmbed.DESCRIPTION_MAX_LENGTH)
            }
            is AllocateResult.WrongMode -> this.failure(
                "This event doesn't randomize teams",
                when (result.event.mode) {
                    EventMode.Servers ->
                        "**${this.escape(result.event.name)}** takes teams from Discord roles, so there's nothing to draw."
                    else ->
                        "**${this.escape(result.event.name)}** lets players pick their own teams, so there's nothing to draw."
                }
            )
            AllocateResult.NoPlayers -> this.failure("Nobody has registered", "There's nobody to put on a team.")
            AllocateResult.NoTeams -> this.failure("There are no teams", "Create some with `/team create` first.")
            is EventUnavailable -> this.unavailable(result)
        }
    }

    fun join(result: JoinResult): MessageEmbed {
        return when (result) {
            is JoinResult.Joined -> Embed {
                title = "You're on ${escape(result.team.name)}"
                color = SUCCESS
                description = if (result.previous == null) {
                    "Anyone can move between teams while registration is open."
                } else {
                    "You've left **${escape(result.previous.name)}**."
                }
            }
            is JoinResult.AlreadyOnTeam -> this.notice(
                "You're already on ${this.escape(result.team.name)}",
                "Nothing changed."
            )
            is JoinResult.TeamFull -> this.teamFull(result.team, result.teamSize, "Pick another one.")
            is JoinResult.SpectatorTeam -> this.spectatorTeam(
                result.team,
                "Use **Spectate** (or `/me spectate`) to watch instead of playing."
            )
            is JoinResult.WrongMode -> this.failure(
                "You can't pick your own team",
                when (result.event.mode) {
                    EventMode.Servers ->
                        "**${this.escape(result.event.name)}** takes teams from Discord roles."
                    else ->
                        "**${this.escape(result.event.name)}** draws teams randomly before the event."
                }
            )
            JoinResult.NotRegistered -> this.failure(
                "You aren't registered yet",
                "Register for the event first, then pick a team."
            )
            is EventUnavailable -> this.unavailable(result)
        }
    }

    fun add(result: AddResult): MessageEmbed {
        return when (result) {
            is AddResult.Added -> this.notice(
                "Registered ${this.escape(result.username)}",
                this.placement(result.team, result.mode) + this.unlinkedHint(result.discordId)
            )
            is AddResult.Moved -> this.notice(
                "Moved ${this.escape(result.username)}",
                buildString {
                    if (result.previous != null) {
                        append("Taken off **${escape(result.previous.name)}**. ")
                    }
                    append(
                        if (result.team == null) {
                            "They have no team now."
                        } else {
                            "They're on **${escape(result.team.name)}** now."
                        }
                    )
                    append(unlinkedHint(result.discordId))
                }
            )
            is AddResult.AlreadyThere -> this.notice(
                "${this.escape(result.username)} is already registered",
                if (result.team == null) {
                    "They're already in the pool, without a team."
                } else {
                    "They're already on **${this.escape(result.team.name)}**."
                }
            )
            is AddResult.TeamFull -> this.teamFull(result.team, result.teamSize)
            is AddResult.SpectatorTeam -> this.spectatorTeam(
                result.team,
                "Spectating is tracked per player. They can use `/me spectate` themselves."
            )
            is AddResult.DiscordTaken -> this.failure(
                "That Discord account is already registered",
                "<@${result.discordId}> is registered as **${this.escape(result.username)}**. " +
                    "Take that registration out with `/team remove` first."
            )
            AddResult.EventFull -> this.eventFull()
            is EventUnavailable -> this.unavailable(result)
        }
    }

    fun remove(result: RemoveResult): MessageEmbed {
        return when (result) {
            is RemoveResult.Removed -> this.notice(
                "Removed ${this.escape(result.username)}",
                if (result.team == null) {
                    "They're no longer registered for the event."
                } else {
                    "They're off **${this.escape(result.team.name)}** and no longer registered."
                }
            )
            is RemoveResult.NotRegistered -> this.notice(
                "${this.escape(result.username)} isn't registered",
                "There's nothing to remove."
            )
            is EventUnavailable -> this.unavailable(result)
        }
    }

    fun noStats(username: String): MessageEmbed {
        return this.failure(
            "No statistics for ${this.escape(username)}",
            "Nothing has been recorded for that player yet."
        )
    }

    fun nextEvent(name: String, time: Long, description: String): MessageEmbed {
        return Embed {
            this.color = 0xAA9BFF
            this.description = "# ${escape(name)}\n" +
                "* Time and Date: <t:$time:F> (<t:$time:R>)\n$description\n\n" +
                "Registration is open. Use the panel to sign up."
        }
    }

    fun noEventScheduled(): MessageEmbed {
        return Embed {
            this.color = 0xFF4B4B
            this.description = "# No Event is Currently Scheduled\n" +
                "There is usually only one event every month. Check back later for updates!"
        }
    }

    fun organizersOnly(): MessageEmbed {
        return this.failure("Only organizers can do that", "Stop poking around, you aren't meant to touch this!")
    }

    fun needsUsername(): MessageEmbed {
        return this.failure(
            "We need your Minecraft username",
            "Try again and enter it when prompted."
        )
    }

    fun unknownUsername(username: String): MessageEmbed {
        return this.failure(
            "No Minecraft account named ${this.escape(username)}",
            "Check the spelling and try again."
        )
    }

    fun usernameTaken(username: String, discordId: Long): MessageEmbed {
        return this.failure(
            "${this.escape(username)} is already registered",
            "That Minecraft account was registered by <@$discordId>."
        )
    }

    fun unknownTeam(name: String): MessageEmbed {
        return this.failure(
            "There's no team called ${this.escape(name)}",
            "Pick one from the suggestions as you type."
        )
    }

    fun duplicateTeam(name: String): MessageEmbed {
        return this.failure(
            "There's already a team called ${this.escape(name)}",
            "Pick a different name."
        )
    }

    fun badColour(example: String): MessageEmbed {
        return this.failure(
            "That colour didn't make sense",
            "Give a hex colour like `$example`."
        )
    }

    fun teamFull(team: DiscordTeam, teamSize: Int, hint: String = ""): MessageEmbed {
        return this.failure(
            "${this.escape(team.name)} is full",
            "That team already has $teamSize players.${if (hint.isEmpty()) "" else " $hint"}"
        )
    }

    fun spectatorTeam(team: DiscordTeam, detail: String): MessageEmbed {
        return this.failure("${this.escape(team.name)} isn't a team you join", detail)
    }

    fun eventFull(): MessageEmbed {
        return this.failure("The event is full", "Every spot has been taken.")
    }

    fun wentWrong(detail: String): MessageEmbed {
        return this.failure("Something went wrong", detail)
    }

    fun failure(heading: String, detail: String): MessageEmbed {
        return Embed {
            title = heading
            color = FAILURE
            description = detail
        }
    }

    fun notice(heading: String, detail: String): MessageEmbed {
        return Embed {
            title = heading
            color = NEUTRAL
            description = detail
        }
    }

    private fun header(event: BotEvent): String {
        return "**${this.stateLabel(event)}** | ${this.modeLabel(event.mode)}\n"
    }

    private fun listDrift(drift: List<Drift>, limit: Int): String {
        return buildString {
            append(drift.take(limit).joinToString("\n") { describe(it) })
            if (drift.size > limit) {
                append("\n... and ${drift.size - limit} more")
            }
        }
    }

    private fun placement(team: DiscordTeam?, mode: EventMode): String {
        if (team != null) {
            return "They're playing for **${this.escape(team.name)}**."
        }
        return when (mode) {
            EventMode.Randomized -> "They're in the pool. `/event allocate` will draw them a team."
            EventMode.Manual -> "They have no team yet. Anyone can put them on one with `/team add`."
            EventMode.Servers -> "They have no team yet. Add them to one with `/team add`."
        }
    }

    private fun unlinkedHint(discordId: Long?): String {
        if (discordId != null) {
            return ""
        }
        return "\n\nNo Discord account is linked to them, so they can't check or change their own " +
            "registration. Run the command again with `user:` to link one."
    }
}
