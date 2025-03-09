package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.casual.bot.CasualBot
import net.casual.bot.util.CommandUtils
import net.casual.bot.util.CommandUtils.isAdministrator
import net.casual.bot.util.DatabaseUtils.getOrCreateDiscordPlayer
import net.casual.bot.util.EmbedUtil
import net.casual.bot.util.TwistedUtils
import net.casual.bot.util.impl.LoadingMessage
import net.casual.bot.util.impl.TeamData
import net.casual.database.DiscordPlayer
import net.casual.database.DiscordTeam
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

object EventCommand : Command {
    private val admin = setOf("size", "randomize", "list", "sync", "clear", "status")

    override val name = "event"

    override val description = "Commands to manage an event with teams and players"

    override fun build(command: SlashCommandData) {
        command.subcommand("join", "Join an event") {
            option<String>("username", "The username to join", true)
        }
        command.subcommand("leave", "Leave the event")
        command.subcommand("spectate", "Spectate the event") {
            option<String>("username", "The username to spectate", true)
        }
        command.subcommand("size", "Set the maximum number of players") {
            option<Int>("size-limit", "Max players for the event", true)
        }
        command.subcommand("randomize", "Randomly assign players to teams") {
            option<Int>("size", "Size of the teams", true)
        }
        command.subcommand("list", "List all registered players") {}
        command.subcommand("sync", "Sync the event data") {}
        command.subcommand("clear", "Clear all teams") {}
        command.subcommand("status", "Toggle the status of the event")
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val data = loadTeams()

        val status = !data.status

        if (status && !command.isAdministrator()) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Event registration is closed. You cannot join, leave, or participate.")).queue()
            return
        }

        if ((command.subcommandName in admin && !command.isAdministrator()) || !TwistedUtils.isTwistedDatabase(CasualBot.config.databaseLogin.name)) {
            loading.replace(EmbedUtil.noPermission()).queue()
            return
        }

        when (command.subcommandName) {
            "join" -> joinEvent(command, loading)
            "leave" -> leaveEvent(command, loading)
            "spectate" -> spectateEvent(command, loading)
            "size" -> setMaxPlayers(command, loading)
            "randomize" -> randomizeTeams(command, loading)
            "list" -> listPlayers(loading)
            "sync" -> syncTeams(loading)
            "clear" -> clearTeams(loading)
            "status" -> toggleStatus(loading)
        }
    }

    private suspend fun joinEvent(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (profile, username) = CommandUtils.getMojangProfile(command)
        if (profile == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("$username is not a valid username!")).queue()
            return
        }

        // Check if the player is already a spectator
        val player = CasualBot.database.getOrCreateDiscordPlayer(username)
        val existingTeam = CasualBot.database.transaction { player?.team }
        if (existingTeam != null && existingTeam.name == "Spectator") {
            loading.replace(
                EmbedUtil.eventJoinFailure(
                    username,
                    "You are already a spectator! Leave the spectating team first!"
                )
            ).queue()
            return
        }

        val userId = command.user.id
        val data = loadTeams()
        val playersPerTeam = calculatePlayersPerTeam(data.maxPlayers)

        val updatedTeams = ArrayList<TeamData.Team>()
        var joined = false
        var totalPlayers = 0

        for (team in data.teams) {
            val players = ArrayList(team.players)
            totalPlayers += players.size

            // Ensure user is not already in a team
            if (players.any { it.id == userId }) {
                loading.replace(EmbedUtil.eventJoinFailure(username, "You have already registered!")).queue()
                return
            }

            if (!joined && players.size < playersPerTeam) {
                players.add(TeamData.Player(username, userId))
                joined = true
            }

            updatedTeams.add(team.copy(players = players))
        }
        val remainingSpots = data.maxPlayers - totalPlayers - 1

        if (joined) {
            saveTeams(data.copy(teams = updatedTeams, maxPlayers = data.maxPlayers, status = data.status))
            loading.replace(EmbedUtil.eventJoinSuccessEmbed(username, remainingSpots)).queue()
            return
        }
        loading.replace(EmbedUtil.eventFullEmbed(username)).queue()
    }

    private suspend fun leaveEvent(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val userId = command.user.id
        val data = loadTeams()
        val updatedTeams = ArrayList<TeamData.Team>()
        var playerRemoved = false
        var totalPlayers = 0

        for (team in data.teams) {
            val updatedPlayers = ArrayList(team.players)

            playerRemoved = playerRemoved || updatedPlayers.removeIf { player -> player.id == userId }

            totalPlayers += updatedPlayers.size
            updatedTeams.add(team.copy(players = updatedPlayers))
        }
        val remainingSpots = data.maxPlayers - totalPlayers

        if (playerRemoved) {
            saveTeams(data.copy(teams = updatedTeams, maxPlayers = data.maxPlayers, status = data.status))
            loading.replace(EmbedUtil.eventLeaveSuccessEmbed(remainingSpots)).queue()
            return
        }
        loading.replace(
            EmbedUtil.eventLeaveFailureEmbed(
                "You were never registered for the event."
            )
        ).queue()
    }

    private suspend fun spectateEvent(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val username = command.getOption<String>("username")!!
        val spectators = CasualBot.database.getDiscordTeam("Spectator")
            ?: throw IllegalStateException("Spectators team was missing from database, expected it to exist!")

        // Check if the player is already in a team
        val data = loadTeams()
        for (team in data.teams) {
            if ((team.players.any { it.username == username }) || (team.players.any { it.id == command.user.id }) ) {
                loading.replace(
                    EmbedUtil.eventJoinFailure(
                        username,
                        "You are currently registered for the event! Please leave the event first!"
                    )
                ).queue()
                return
            }
        }

        val player = CasualBot.database.getOrCreateDiscordPlayer(username)
        if (player == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid player: $username")).queue()
            return
        }
        val existingTeam = CasualBot.database.transaction { player.team }
        if (existingTeam != null) {
            // Essentially makes the command a toggle so we don't need a separate one
            CasualBot.database.transaction {
                player.team = null
            }

            loading.replace(EmbedUtil.removePlayerSuccessEmbed(player, spectators)).queue()
            return
        }

        CasualBot.database.transaction {
            player.team = spectators
        }
        loading.replace(EmbedUtil.addPlayerSuccessEmbed(player, spectators)).queue()
    }


    private suspend fun setMaxPlayers(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val sizeLimit = command.getOption<Int>("size-limit")!!
        val data = loadTeams()

        saveTeams(data.copy(teams = data.teams,  maxPlayers = sizeLimit, status = data.status))
        loading.replace("Max players set to $sizeLimit").queue()
    }

    private suspend fun randomizeTeams(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val data = loadTeams()
        val playersPerTeam = command.getOption<Int>("size") ?: 2

        val shuffledPlayers = data.teams.flatMap { it.players }.shuffled()
        val newTeams = data.teams.mapTo(ArrayList()) { it.copy(players = listOf()) }

        var teamIndex = 0
        val numTeams = (shuffledPlayers.size + playersPerTeam - 1) / playersPerTeam
        for (player in shuffledPlayers) {
            val team = newTeams[teamIndex]
            newTeams[teamIndex] = team.copy(players = team.players + player)
            teamIndex = (teamIndex + 1) % numTeams
        }

        saveTeams(data.copy(teams = newTeams, maxPlayers = data.maxPlayers, status = data.status))

        loading.replace("Teams have been randomized with $playersPerTeam players per team!").queue()
    }

    private suspend fun listPlayers(loading: LoadingMessage) {
        val data = loadTeams()

        val hasAtLeastOneTeam = data.teams.any { team -> team.players.isNotEmpty() }
        if (!hasAtLeastOneTeam) {
            loading.replace("No teams or players are currently registered.").queue()
            return
        }

        val formattedTeams = data.teams.joinToString("\n\n") { team ->
            "**${team.name}:**\n" + if (team.players.isNotEmpty()) {
                team.players.joinToString("\n") { player ->
                    "- ${MarkdownSanitizer.sanitize(player.username, MarkdownSanitizer.SanitizationStrategy.ESCAPE)}"
                }
            } else {
                "No players in this team."
            }
        }

        val team = CasualBot.database.getDiscordTeam("Spectator")
            ?: throw IllegalStateException("Spectators team was missing from database, expected it to exist!")
        val spectators = EmbedUtil.currentMembers(team)

        val message = buildString {
            append("\n\n")
            append(formattedTeams)
            append("\n\n")
            append("**Spectators:**\n$spectators")
        }

        loading.replace(message).queue()
    }

    private suspend fun syncTeams(loading: LoadingMessage) {
        val data = loadTeams()

        val updates = ArrayList<Pair<DiscordPlayer, DiscordTeam>>()
        for (team in data.teams) {
            val dbTeam = CasualBot.database.getDiscordTeam(team.name)
            if (dbTeam == null) {
                loading.replace(EmbedUtil.somethingWentWrongEmbed("Could not find team: ${team.name}")).queue()
                continue
            }

            for (player in team.players) {
                val dbPlayer = CasualBot.database.getOrCreateDiscordPlayer(player.username)

                if (dbPlayer == null) {
                    loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid player: ${player.username}")).queue()
                    continue
                }

                updates.add(dbPlayer to dbTeam)
            }
        }

        CasualBot.database.transaction {
            clearDatabaseTeams()
            for ((player, team) in updates) {
                player.refresh()
                player.team = team
            }
        }

        loading.replace("Teams have been successfully synced!").queue()
    }

    private suspend fun clearTeams(loading: LoadingMessage) {
        val data = loadTeams()
        saveTeams(data.copy(teams = data.teams.map { it.copy(players = listOf()) }, maxPlayers = data.maxPlayers, status = data.status))
        this.clearDatabaseTeams()
        loading.replace("All teams have been cleared!").queue()
    }

    private suspend fun toggleStatus(loading: LoadingMessage) {
        val data = loadTeams()

        data.status = !data.status

        saveTeams(data)

        val statusMessage = if (data.status) {
            "Event registration is now **OPEN**."
        } else {
            "Event registration is now **CLOSED**."
        }

        loading.replace(statusMessage).queue()
    }

    private fun clearDatabaseTeams() {
        CasualBot.database.transaction {
            val players = CasualBot.database.getDiscordPlayers()
            for (player in players) {
                player.team = null
            }
        }
    }

    private fun calculatePlayersPerTeam(maxPlayers: Int): Int {
        return when {
            maxPlayers <= 32 -> 2
            maxPlayers <= 48 -> 3
            maxPlayers <= 52 -> 4
            else -> 5
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadTeams(): TeamData {
        val path = Path("teams.json")
        if (path.notExists()) {
            val default = TeamData()
            path.outputStream().use {
                json.encodeToStream<TeamData>(default, it)
            }
            return default
        }
        return path.inputStream().use {
            json.decodeFromStream<TeamData>(it)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun saveTeams(teams: TeamData) {
        val path = Path("teams.json")
        path.outputStream().use {
            json.encodeToStream(teams , it)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
}
