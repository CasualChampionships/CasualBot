package net.casual.bot.event

import net.casual.bot.database.BotEvent
import net.casual.bot.database.EventMode
import net.casual.bot.database.EventState
import net.casual.database.DiscordPlayer
import net.casual.database.DiscordTeam

sealed interface RegisterResult {
    data class Registered(
        val username: String,
        val team: DiscordTeam?,
        val spotsRemaining: Int,
        val wasSpectating: Boolean
    ): RegisterResult

    data object NeedsUsername: RegisterResult
    data object AlreadyRegistered: RegisterResult
    data object NoTeamRole: RegisterResult
    data class AmbiguousTeamRoles(val teams: List<String>): RegisterResult
    data class TeamFull(val team: DiscordTeam, val teamSize: Int): RegisterResult
    data class UnknownUsername(val username: String): RegisterResult
    data class UsernameTaken(val username: String, val discordId: Long): RegisterResult
    data object EventFull: RegisterResult
}

sealed interface LeaveResult {
    data class Left(val spotsRemaining: Int): LeaveResult
    data object NotRegistered: LeaveResult
}

sealed interface SpectateResult {
    data class Spectating(val wasPlaying: Boolean): SpectateResult
    data object AlreadySpectating: SpectateResult
    data object NeedsUsername: SpectateResult
    data class UnknownUsername(val username: String): SpectateResult
    data class UsernameTaken(val username: String, val discordId: Long): SpectateResult
}

sealed interface EventUnavailable:
    RegisterResult, LeaveResult, SpectateResult, JoinResult,
    AllocateResult, AddResult, RemoveResult {
    data object NoActiveEvent: EventUnavailable
    data class NotAccepting(val state: EventState, val archived: Boolean): EventUnavailable
}

sealed interface JoinResult {
    data class Joined(val team: DiscordTeam, val previous: DiscordTeam?): JoinResult
    data class AlreadyOnTeam(val team: DiscordTeam): JoinResult
    data class TeamFull(val team: DiscordTeam, val teamSize: Int): JoinResult
    data class WrongMode(val event: BotEvent): JoinResult
    data class SpectatorTeam(val team: DiscordTeam): JoinResult
    data object NotRegistered: JoinResult
}

sealed interface AddResult {
    data class Added(
        val username: String,
        val team: DiscordTeam?,
        val mode: EventMode,
        val discordId: Long?
    ): AddResult

    data class Moved(
        val username: String,
        val team: DiscordTeam?,
        val previous: DiscordTeam?,
        val discordId: Long?
    ): AddResult

    data class AlreadyThere(val username: String, val team: DiscordTeam?): AddResult
    data class TeamFull(val team: DiscordTeam, val teamSize: Int): AddResult
    data class SpectatorTeam(val team: DiscordTeam): AddResult
    data class DiscordTaken(val username: String, val discordId: Long): AddResult
    data object EventFull: AddResult
}

sealed interface RemoveResult {
    data class Removed(val username: String, val team: DiscordTeam?): RemoveResult
    data class NotRegistered(val username: String): RemoveResult
}

sealed interface AllocateResult {
    data class Allocated(val teams: List<AllocatedTeam>, val unallocated: List<String>): AllocateResult
    data class WrongMode(val event: BotEvent): AllocateResult
    data object NoPlayers: AllocateResult
    data object NoTeams: AllocateResult
}

data class AllocatedTeam(val team: DiscordTeam, val players: List<DiscordPlayer>)

data class RegisteredPlayers(val playing: List<String>, val spectating: List<String>) {
    val total: Int get() = this.playing.size + this.spectating.size
}

data class TeamRoster(
    val team: DiscordTeam,
    val members: List<String>,
    val capacity: Int
) {
    val isFull: Boolean get() = this.members.size >= this.capacity
}

data class EventSummary(
    val event: BotEvent,
    val playing: Int,
    val spectating: Int,
    val unallocated: Int,
    val capacity: Int,
    val rosters: List<TeamRoster>
) {
    val spotsRemaining: Int get() = (this.capacity - this.playing).coerceAtLeast(0)
}

sealed interface Drift {
    data class OnRosterWithoutRole(val username: String, val team: String): Drift
    data class HasRoleNotRegistered(val discordId: Long, val team: String): Drift
    data class RegisteredToWrongTeam(val username: String, val onRoster: String, val byRole: String): Drift
    data class NoDiscordAccount(val username: String, val team: String): Drift
}
