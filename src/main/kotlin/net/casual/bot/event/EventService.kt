package net.casual.bot.event

import net.casual.bot.CasualBot
import net.casual.bot.config.BotConfig
import net.casual.bot.database.BotEvent
import net.casual.bot.database.BotEvents
import net.casual.bot.database.EventMode
import net.casual.bot.database.EventState
import net.casual.bot.database.Registration
import net.casual.bot.database.BotRegistrations
import net.casual.bot.database.BotDatabase.activeEvent
import net.casual.bot.database.BotDatabase.latestEvent
import net.casual.bot.database.BotDatabase.linkPlayer
import net.casual.bot.database.BotDatabase.linkedDiscordId
import net.casual.bot.database.BotDatabase.linkedPlayer
import net.casual.bot.database.BotDatabase.registrationOf
import net.casual.bot.utils.DatabaseUtils.getOrCreateDiscordPlayer
import net.casual.database.DiscordPlayer
import net.casual.database.DiscordTeam
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import java.time.Instant

object EventService {
    fun activeEvent(): BotEvent? {
        return CasualBot.database.activeEvent()
    }

    fun latestEvent(): BotEvent? {
        return CasualBot.database.latestEvent()
    }

    fun editableEvent(force: Boolean): BotEvent? {
        return if (force) this.latestEvent() else this.activeEvent()
    }

    fun createEvent(name: String, mode: EventMode, teamSize: Int): BotEvent {
        return CasualBot.database.transaction {
            BotEvent.find { BotEvents.archived eq false }.forEach { it.archived = true }

            BotEvent.new {
                this.name = name
                this.mode = mode
                this.state = EventState.Closed
                this.teamSize = teamSize
                this.archived = false
                this.createdAt = Instant.now().epochSecond
            }
        }
    }

    fun setState(event: BotEvent, state: EventState) {
        CasualBot.database.transaction {
            event.state = state
        }
    }

    fun archive(event: BotEvent) {
        CasualBot.database.transaction {
            event.archived = true
        }
    }

    suspend fun register(
        discordId: Long,
        roleIds: Collection<Long>,
        username: String? = null
    ): RegisterResult {
        val event = this.activeEvent() ?: return EventUnavailable.NoActiveEvent
        if (!event.acceptingRegistrations) {
            return EventUnavailable.NotAccepting(event.state, event.archived)
        }

        val existing = CasualBot.database.registrationOf(event, discordId)
        if (existing != null) {
            if (!CasualBot.database.transaction { existing.spectating }) {
                return RegisterResult.AlreadyRegistered
            }
            return this.stopSpectating(event, existing, roleIds)
        }

        val player = when (val resolved = this.resolvePlayer(discordId, username)) {
            is PlayerLookup.Found -> resolved.player
            PlayerLookup.NeedsUsername -> return RegisterResult.NeedsUsername
            is PlayerLookup.Unknown -> return RegisterResult.UnknownUsername(resolved.username)
        }

        val holder = CasualBot.database.registrationOf(event, player)
        if (holder != null) {
            return this.claimToPlay(event, holder, discordId, player)
        }

        val team = when (val resolved = this.teamFor(event, roleIds)) {
            is TeamLookup.Found -> resolved.team
            is TeamLookup.Failed -> return resolved.reason
        }

        val now = Instant.now().epochSecond
        val result = CasualBot.database.transaction {
            takeSpot(event, team) {
                Registration.new {
                    this.event = event
                    this.discordId = discordId
                    this.player = player
                    this.team = team
                    this.spectating = false
                    this.registeredAt = now
                }
                RegisterResult.Registered(player.name, team, it, false)
            }
        }
        if (result is RegisterResult.Registered) {
            CasualBot.database.linkPlayer(discordId, player, now)
        }
        return result
    }

    private fun claimToPlay(
        event: BotEvent,
        registration: Registration,
        discordId: Long,
        player: DiscordPlayer
    ): RegisterResult {
        val owner = CasualBot.database.transaction { registration.discordId }
        if (owner != null) {
            return RegisterResult.UsernameTaken(player.name, owner)
        }

        val now = Instant.now().epochSecond
        val team = CasualBot.database.transaction {
            registration.discordId = discordId
            registration.spectating = false
            registration.team
        }
        CasualBot.database.linkPlayer(discordId, player, now)
        return RegisterResult.Registered(player.name, team, this.spotsRemaining(event), false)
    }

    private fun stopSpectating(
        event: BotEvent,
        registration: Registration,
        roleIds: Collection<Long>
    ): RegisterResult {
        val team = when (val resolved = this.teamFor(event, roleIds)) {
            is TeamLookup.Found -> resolved.team
            is TeamLookup.Failed -> return resolved.reason
        }

        return CasualBot.database.transaction {
            takeSpot(event, team) {
                registration.spectating = false
                registration.team = team
                RegisterResult.Registered(registration.player.name, team, it, true)
            }
        }
    }

    private fun takeSpot(
        event: BotEvent,
        team: DiscordTeam?,
        claim: (spotsRemaining: Int) -> RegisterResult
    ): RegisterResult {
        if (team != null && this.countPlaying(event, team) >= event.teamSize) {
            return RegisterResult.TeamFull(team, event.teamSize)
        }
        val capacity = capacity(event)
        val playing = this.countPlaying(event, null)
        if (team == null && playing >= capacity) {
            return RegisterResult.EventFull
        }
        return claim((capacity - (playing + 1)).coerceAtLeast(0))
    }

    fun leave(discordId: Long): LeaveResult {
        val event = this.activeEvent() ?: return EventUnavailable.NoActiveEvent
        if (!event.acceptingRegistrations) {
            return EventUnavailable.NotAccepting(event.state, event.archived)
        }

        val registration = CasualBot.database.registrationOf(event, discordId)
            ?: return LeaveResult.NotRegistered

        CasualBot.database.transaction { registration.delete() }
        return LeaveResult.Left(this.spotsRemaining(event))
    }

    suspend fun spectate(discordId: Long, username: String? = null): SpectateResult {
        val event = this.activeEvent() ?: return EventUnavailable.NoActiveEvent
        if (!event.acceptingRegistrations) {
            return EventUnavailable.NotAccepting(event.state, event.archived)
        }

        val existing = CasualBot.database.registrationOf(event, discordId)
        if (existing != null) {
            return CasualBot.database.transaction {
                if (existing.spectating) {
                    return@transaction SpectateResult.AlreadySpectating
                }
                existing.spectating = true
                existing.team = null
                SpectateResult.Spectating(true)
            }
        }

        val player = when (val resolved = this.resolvePlayer(discordId, username)) {
            is PlayerLookup.Found -> resolved.player
            PlayerLookup.NeedsUsername -> return SpectateResult.NeedsUsername
            is PlayerLookup.Unknown -> return SpectateResult.UnknownUsername(resolved.username)
        }

        val holder = CasualBot.database.registrationOf(event, player)
        if (holder != null) {
            return this.claimToSpectate(holder, discordId, player)
        }

        val now = Instant.now().epochSecond
        CasualBot.database.transaction {
            Registration.new {
                this.event = event
                this.discordId = discordId
                this.player = player
                this.team = null
                this.spectating = true
                this.registeredAt = now
            }
        }
        CasualBot.database.linkPlayer(discordId, player, now)
        return SpectateResult.Spectating(false)
    }

    private fun claimToSpectate(
        registration: Registration,
        discordId: Long,
        player: DiscordPlayer
    ): SpectateResult {
        val owner = CasualBot.database.transaction { registration.discordId }
        if (owner != null) {
            return SpectateResult.UsernameTaken(player.name, owner)
        }

        val now = Instant.now().epochSecond
        val wasPlaying = CasualBot.database.transaction {
            val playing = !registration.spectating
            registration.discordId = discordId
            registration.spectating = true
            registration.team = null
            playing
        }
        CasualBot.database.linkPlayer(discordId, player, now)
        return SpectateResult.Spectating(wasPlaying)
    }

    fun join(discordId: Long, team: DiscordTeam): JoinResult {
        val event = this.activeEvent() ?: return EventUnavailable.NoActiveEvent
        if (!event.acceptingRegistrations) {
            return EventUnavailable.NotAccepting(event.state, event.archived)
        }
        if (event.mode != EventMode.Manual) {
            return JoinResult.WrongMode(event)
        }
        if (this.isSpectatorTeam(team)) {
            return JoinResult.SpectatorTeam(team)
        }

        val registration = CasualBot.database.registrationOf(event, discordId)
            ?: return JoinResult.NotRegistered

        return CasualBot.database.transaction {
            val previous = registration.team
            if (previous?.id == team.id && !registration.spectating) {
                return@transaction JoinResult.AlreadyOnTeam(team)
            }
            if (countPlaying(event, team) >= event.teamSize) {
                return@transaction JoinResult.TeamFull(team, event.teamSize)
            }
            registration.spectating = false
            registration.team = team
            JoinResult.Joined(team, previous)
        }
    }

    fun addPlayer(
        player: DiscordPlayer,
        team: DiscordTeam?,
        discordId: Long? = null,
        force: Boolean = false
    ): AddResult {
        val event = this.editableEvent(force) ?: return EventUnavailable.NoActiveEvent
        if (!force && !event.acceptingRegistrations) {
            return EventUnavailable.NotAccepting(event.state, event.archived)
        }
        if (team != null && this.isSpectatorTeam(team)) {
            return AddResult.SpectatorTeam(team)
        }

        val existing = CasualBot.database.registrationOf(event, player)
        val owner = discordId ?: CasualBot.database.linkedDiscordId(player)
        if (owner != null) {
            val holder = CasualBot.database.registrationOf(event, owner)
            if (holder != null && holder.id != existing?.id) {
                return AddResult.DiscordTaken(
                    CasualBot.database.transaction { holder.player.name },
                    owner
                )
            }
        }

        val now = Instant.now().epochSecond
        val result = CasualBot.database.transaction {
            if (existing == null) {
                if (team != null && countPlaying(event, team) >= event.teamSize) {
                    return@transaction AddResult.TeamFull(team, event.teamSize)
                }
                if (team == null && countPlaying(event, null) >= capacity(event)) {
                    return@transaction AddResult.EventFull
                }
                Registration.new {
                    this.event = event
                    this.discordId = owner
                    this.player = player
                    this.team = team
                    this.spectating = false
                    this.registeredAt = now
                }
                return@transaction AddResult.Added(player.name, team, event.mode, owner)
            }

            if (discordId != null) {
                existing.discordId = discordId
            }
            if (existing.team?.id == team?.id && !existing.spectating) {
                return@transaction AddResult.AlreadyThere(player.name, team)
            }
            if (team != null && countPlaying(event, team) >= event.teamSize) {
                return@transaction AddResult.TeamFull(team, event.teamSize)
            }

            val previous = existing.team
            existing.team = team
            existing.spectating = false
            AddResult.Moved(player.name, team, previous, existing.discordId)
        }

        if (discordId != null && result !is AddResult.TeamFull && result !is AddResult.EventFull) {
            CasualBot.database.linkPlayer(discordId, player, now)
        }
        return result
    }

    fun removePlayer(player: DiscordPlayer, force: Boolean = false): RemoveResult {
        val event = this.editableEvent(force) ?: return EventUnavailable.NoActiveEvent
        val registration = CasualBot.database.registrationOf(event, player)
            ?: return RemoveResult.NotRegistered(player.name)

        return CasualBot.database.transaction {
            val team = registration.team
            registration.delete()
            RemoveResult.Removed(player.name, team)
        }
    }

    fun roster(team: DiscordTeam): List<String>? {
        val event = this.activeEvent() ?: return null
        return CasualBot.database.transaction {
            Registration.find {
                (BotRegistrations.event eq event.id) and
                    (BotRegistrations.team eq team.id) and
                    (BotRegistrations.spectating eq false)
            }.map { it.player.name }.sorted()
        }
    }

    fun allocate(apply: Boolean): AllocateResult {
        val event = this.activeEvent() ?: return EventUnavailable.NoActiveEvent
        if (event.mode != EventMode.Randomized) {
            return AllocateResult.WrongMode(event)
        }
        val teams = this.playingTeams()
        if (teams.isEmpty()) {
            return AllocateResult.NoTeams
        }

        val players = CasualBot.database.transaction {
            playing(event).map { it to it.player }
        }
        if (players.isEmpty()) {
            return AllocateResult.NoPlayers
        }

        val (filled, unallocated) = this.distribute(players.shuffled(), teams.size, event.teamSize)
        val buckets = teams.take(filled.size).zip(filled)

        return CasualBot.database.transaction {
            if (apply) {
                for ((team, entries) in buckets) {
                    for ((registration, _) in entries) {
                        registration.team = team
                    }
                }
                for ((registration, _) in unallocated) {
                    registration.team = null
                }
            }
            AllocateResult.Allocated(
                buckets.filter { it.second.isNotEmpty() }
                    .map { (team, entries) -> AllocatedTeam(team, entries.map { it.second }) },
                unallocated.map { it.second.name }
            )
        }
    }

    fun summarise(event: BotEvent): EventSummary {
        return CasualBot.database.transaction {
            val registrations = event.registrations.toList()
            val playing = registrations.filter { !it.spectating }
            val rosters = playingTeams().map { team ->
                TeamRoster(
                    team,
                    playing.filter { it.team?.id == team.id }.map { it.player.name }.sorted(),
                    event.teamSize
                )
            }
            EventSummary(
                event,
                playing.size,
                registrations.count { it.spectating },
                playing.count { it.team == null },
                capacity(event),
                rosters
            )
        }
    }

    fun drift(event: BotEvent, roleHolders: Map<Long, Set<Long>>): List<Drift> {
        if (event.mode != EventMode.Servers) {
            return listOf()
        }
        return CasualBot.database.transaction {
            val found = ArrayList<Drift>()
            val teams = playingTeams().filter { it.roleId != null }
            val registrations = event.registrations.filter { !it.spectating }

            for (registration in registrations) {
                val onRoster = registration.team ?: continue
                val discordId = registration.discordId
                if (discordId == null) {
                    found.add(Drift.NoDiscordAccount(registration.player.name, onRoster.name))
                    continue
                }
                val holders = onRoster.roleId?.let { roleHolders[it] } ?: continue
                if (discordId in holders) {
                    continue
                }
                val byRole = teams.firstOrNull { discordId in (roleHolders[it.roleId] ?: setOf()) }
                if (byRole == null) {
                    found.add(Drift.OnRosterWithoutRole(registration.player.name, onRoster.name))
                } else {
                    found.add(Drift.RegisteredToWrongTeam(registration.player.name, onRoster.name, byRole.name))
                }
            }

            val registered = registrations.map { it.discordId }.toSet()
            for (team in teams) {
                for (holder in roleHolders[team.roleId] ?: setOf()) {
                    if (holder !in registered) {
                        found.add(Drift.HasRoleNotRegistered(holder, team.name))
                    }
                }
            }
            found
        }
    }

    fun <T> distribute(items: List<T>, teamCount: Int, teamSize: Int): Pair<List<List<T>>, List<T>> {
        if (items.isEmpty() || teamCount <= 0 || teamSize <= 0) {
            return listOf<List<T>>() to items
        }
        val required = (items.size + teamSize - 1) / teamSize
        val buckets = List(minOf(required, teamCount)) { ArrayList<T>() }
        val leftover = ArrayList<T>()
        for ((index, item) in items.withIndex()) {
            val bucket = buckets[index % buckets.size]
            if (bucket.size >= teamSize) {
                leftover.add(item)
            } else {
                bucket.add(item)
            }
        }
        return buckets to leftover
    }

    fun isSpectatorTeam(team: DiscordTeam): Boolean {
        return team.name.equals(BotConfig.read { spectatorTeam }, true)
    }

    fun spectatorTeam(): DiscordTeam? {
        return CasualBot.database.getDiscordTeam(BotConfig.read { spectatorTeam })
    }

    fun playingTeams(): List<DiscordTeam> {
        return CasualBot.database.getDiscordTeams().filter { !this.isSpectatorTeam(it) }
    }

    fun capacity(event: BotEvent): Int {
        return when (event.mode) {
            EventMode.Servers -> this.playingTeams().count { it.roleId != null } * event.teamSize
            EventMode.Randomized, EventMode.Manual -> this.playingTeams().size * event.teamSize
        }
    }

    private fun spotsRemaining(event: BotEvent): Int {
        return (this.capacity(event) - this.playingCount(event)).coerceAtLeast(0)
    }

    private fun playing(event: BotEvent): List<Registration> {
        return Registration.find {
            (BotRegistrations.event eq event.id) and (BotRegistrations.spectating eq false)
        }.toList()
    }

    private fun playingCount(event: BotEvent): Int {
        return CasualBot.database.transaction { countPlaying(event, null) }
    }

    private fun countPlaying(event: BotEvent, team: DiscordTeam?): Int {
        val base = (BotRegistrations.event eq event.id) and (BotRegistrations.spectating eq false)
        val condition = if (team == null) base else base and (BotRegistrations.team eq team.id)
        return Registration.find(condition).count().toInt()
    }

    private fun teamFor(event: BotEvent, roleIds: Collection<Long>): TeamLookup {
        if (event.mode != EventMode.Servers) {
            return TeamLookup.Found(null)
        }
        val matches = this.playingTeams().filter { it.roleId != null && it.roleId in roleIds }
        return when (matches.size) {
            0 -> TeamLookup.Failed(RegisterResult.NoTeamRole)
            1 -> TeamLookup.Found(matches.single())
            else -> TeamLookup.Failed(RegisterResult.AmbiguousTeamRoles(matches.map { it.name }))
        }
    }

    private suspend fun resolvePlayer(discordId: Long, username: String?): PlayerLookup {
        if (username == null) {
            val linked = CasualBot.database.linkedPlayer(discordId) ?: return PlayerLookup.NeedsUsername
            return PlayerLookup.Found(linked)
        }
        val player = CasualBot.database.getOrCreateDiscordPlayer(username)
            ?: return PlayerLookup.Unknown(username)
        return PlayerLookup.Found(player)
    }

    private sealed interface PlayerLookup {
        data class Found(val player: DiscordPlayer): PlayerLookup
        data object NeedsUsername: PlayerLookup
        data class Unknown(val username: String): PlayerLookup
    }

    private sealed interface TeamLookup {
        data class Found(val team: DiscordTeam?): TeamLookup
        data class Failed(val reason: RegisterResult): TeamLookup
    }
}
