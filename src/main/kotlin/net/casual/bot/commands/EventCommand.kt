package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import kotlinx.coroutines.delay
import net.casual.bot.util.impl.LoadingMessage
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import java.io.File
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import net.casual.bot.CasualBot
import net.casual.bot.util.CommandUtils
import net.casual.bot.util.CommandUtils.isAdministrator
import net.casual.bot.util.DatabaseUtils.getOrCreateDiscordPlayer
import net.casual.bot.util.EmbedUtil
import net.casual.database.DiscordPlayer
import net.casual.database.DiscordTeam

object EventCommand : Command {
    private val admin = setOf("size", "randomize", "list", "sync", "clear")
    override val name = "event"
    override val description = "Commands to manage an event with teams and players"

    private fun loadTeams(): JsonObject {
        val file = File("teams.json")
        if (!file.exists()) {
            file.createNewFile()
            file.writeText("""{"maxPlayers":32,"teams":[{"id":"AquaAxolotls","players":[]},{"id":"LavenderLions","players":[]},{"id":"GoldenGoats","players":[]},{"id":"RedRhinos","players":[]},{"id":"OrangeOcelots","players":[]},{"id":"PlatinumPanthers","players":[]},{"id":"CobaltCobras","players":[]},{"id":"GreenGophers","players":[]},{"id":"AmberArmadillos","players":[]},{"id":"YellowYetis","players":[]},{"id":"LimeLlamas","players":[]},{"id":"CopperCats","players":[]},{"id":"TealTurtles","players":[]},{"id":"CrimsonCoyotes","players":[]},{"id":"IndigoIguanas","players":[]},{"id":"MagentaMice","players":[]}]}""")
        }
        return Json.parseToJsonElement(file.readText()) as JsonObject
    }

    private fun saveTeams(data: JsonObject) {
        val file = File("teams.json")
        file.writeText(Json.encodeToString(data))
    }

    override fun build(command: SlashCommandData) {
        command.subcommand("join", "Join an event") {
            option<String>("username", "The username to join", true)
        }
        command.subcommand("leave", "Leave the event") {
            option<String>("username", "The username to leave", true)
        }
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
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {

        if (command.subcommandName in admin && !command.isAdministrator() || ((CasualBot.config.databaseLogin.name != "twisted") && (CasualBot.config.databaseLogin.name != "twisted_debug"))) {
            loading.replace(EmbedUtil.noPermission()).queue()
            return
        }


        when (command.subcommandName) {
            "join" -> joinEvent(command, loading)
            "leave" -> leaveEvent(command, loading)
            "spectate" -> spectateEvent(command, loading)
            "size" -> setMaxPlayers(command, loading)
            "randomize" -> randomizeTeams(command, loading)
            "list" -> listPlayers(command, loading)
            "sync" -> syncTeams(command, loading)
            "clear" -> clearTeams(command, loading)
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
        val maxPlayers = data["maxPlayers"]!!.jsonPrimitive.int
        val playersPerTeam = calculatePlayersPerTeam(maxPlayers)

        val teams = data["teams"]!!.jsonArray
        val updatedTeams = mutableListOf<JsonObject>()
        var joined = false
        var totalPlayers = 0

        for (team in teams) {
            val teamObject = team.jsonObject
            val players = teamObject["players"]!!.jsonArray.toMutableList()
            totalPlayers += players.size

            // Ensure user is not already in a team
            if (players.any { it.jsonObject["id"]!!.jsonPrimitive.content == userId }) {
                loading.replace(EmbedUtil.eventJoinFailure(username, "You have already registered!")).queue()
                return
            }

            if (!joined && players.size < playersPerTeam) {
                players.add(buildJsonObject {
                    put("username", username)
                    put("id", userId)
                })
                joined = true
            }

            updatedTeams.add(buildJsonObject {
                put("id", teamObject["id"]!!)
                put("players", buildJsonArray { players.forEach { add(it) } })
            })
        }
        val remainingSpots = maxPlayers - totalPlayers - 1

        if (joined) {
            val updatedData = data.toMutableMap()
            updatedData["teams"] = buildJsonArray { updatedTeams.forEach { add(it) } }
            saveTeams(JsonObject(updatedData))

            loading.replace(EmbedUtil.eventJoinSuccessEmbed(username, remainingSpots)).queue()
        } else {
            loading.replace(EmbedUtil.eventFullEmbed(username)).queue()
        }
    }

    private suspend fun leaveEvent(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (profile, username) = CommandUtils.getMojangProfile(command)
        if (profile == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("$username is not a valid username!")).queue()
            return
        }

        val userId = command.user.id
        val data = loadTeams()
        val maxPlayers = data["maxPlayers"]!!.jsonPrimitive.int
        val teams = data["teams"]!!.jsonArray
        val updatedTeams = mutableListOf<JsonObject>()
        var playerFound = false
        var totalPlayers = 0

        for (team in teams) {
            val players = team.jsonObject["players"]!!.jsonArray.toMutableList()
            val playerIndex = players.indexOfFirst {
                it.jsonObject["id"]!!.jsonPrimitive.content == userId && it.jsonObject["username"]!!.jsonPrimitive.content == username
            }

            if (playerIndex != -1) {
                players.removeAt(playerIndex) // Remove the player entry
                playerFound = true
            }

            totalPlayers += players.size
            updatedTeams.add(buildJsonObject {
                put("id", team.jsonObject["id"]!!)
                put("players", buildJsonArray { players.forEach { add(it) } })
            })
        }
        val remainingSpots = maxPlayers - totalPlayers

        if (playerFound) {
            val updatedData = data.toMutableMap()
            updatedData["teams"] = buildJsonArray { updatedTeams.forEach { add(it) } }
            saveTeams(JsonObject(updatedData))

            loading.replace(EmbedUtil.eventLeaveSuccessEmbed(username, remainingSpots)).queue()

        } else {
            loading.replace(
                EmbedUtil.eventLeaveFailureEmbed(
                    username,
                    "You cannot leave with a different username. Please use the one you joined with."
                )
            ).queue()

        }
    }


    private suspend fun spectateEvent(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val username = command.getOption<String>("username")!!
        val (team) = CasualBot.database.getDiscordTeam("Spectator") to name


        // Check if the player is already in a team
        val data = loadTeams()
        val teams = data["teams"]!!.jsonArray

        for (team in teams) {
            val players = team.jsonObject["players"]!!.jsonArray
            if (players.any { it.jsonObject["id"]!!.jsonPrimitive.content == command.user.id }) {
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

            loading.replace(EmbedUtil.removePlayerSuccessEmbed(player, team!!)).queue()
            return
        }

        CasualBot.database.transaction {
            player.team = team
        }
        loading.replace(EmbedUtil.addPlayerSuccessEmbed(player, team!!)).queue()
    }

    private suspend fun setMaxPlayers(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val sizeLimit = command.getOption<Int>("max-players")!!
        val data = loadTeams()

        val updatedData = data.toMutableMap()
        updatedData["maxPlayers"] = JsonPrimitive(sizeLimit)

        // Save the updated data
        saveTeams(JsonObject(updatedData))
        loading.replace("Max players set to $sizeLimit").queue()
    }

    private suspend fun randomizeTeams(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val data = loadTeams()
        val maxPlayers = data["maxPlayers"]!!.jsonPrimitive.int

        val playersPerTeam = command.getOption("size")?.asInt ?: 2

        // Collect all players while preserving their structure (username & id)
        val allPlayers = data["teams"]!!.jsonArray
            .flatMap { it.jsonObject["players"]!!.jsonArray }
            .map { it.jsonObject }
            .shuffled() // Shuffle the players

        // Make sure empty teams don't get yeeted
        val teamNames = data["teams"]!!.jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }

        val newTeams = mutableMapOf<String, MutableList<JsonObject>>()
        for (teamName in teamNames) {
            newTeams[teamName] = mutableListOf()
        }

        // Evenly Distribute
        var teamIndex = 0
        val numTeams = (allPlayers.size + playersPerTeam - 1) / playersPerTeam
        val activeTeams = teamNames.take(numTeams)

        for (player in allPlayers) {
            val teamName = activeTeams[teamIndex]
            newTeams[teamName]!!.add(player)

            teamIndex = (teamIndex + 1) % activeTeams.size
        }

        val updatedTeams = buildJsonArray {
            for (teamName in teamNames) {
                add(buildJsonObject {
                    put("id", teamName)
                    put("players", buildJsonArray { newTeams[teamName]!!.forEach { add(it) } })
                })
            }
        }

        val updatedData = data.toMutableMap()
        updatedData["teams"] = updatedTeams
        saveTeams(JsonObject(updatedData))

        loading.replace("Teams have been randomized with $playersPerTeam players per team!").queue()
    }

    private suspend fun clearTeams(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val data = loadTeams()

        val updatedTeams = data["teams"]!!.jsonArray.map { team ->
            val teamObject = team.jsonObject
            buildJsonObject {
                put("id", teamObject["id"]!!)
                put("players", buildJsonArray { }) // Empty player list
            }
        }

        val updatedData = data.toMutableMap()
        updatedData["teams"] = buildJsonArray { updatedTeams.forEach { add(it) } }

        saveTeams(JsonObject(updatedData))

        // Database teams
        val players = CasualBot.database.getDiscordPlayers()
        CasualBot.database.transaction {
            for (player in players) {
                player.team = null
            }
        }
        loading.replace("All teams have been cleared!").queue()
    }

    private suspend fun listPlayers(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val data = loadTeams()
        val teams = data["teams"]?.jsonArray ?: buildJsonArray { }

        val hasValidTeams = teams.any { team ->
            val players = team.jsonObject["players"]?.jsonArray ?: return@any false
            players.isNotEmpty()
        }

        if (!hasValidTeams) {
            loading.replace("No teams or players are currently registered.").queue()
            return
        }

        val formattedTeams = teams.joinToString("\n\n") { formatTeam(it) }

        // Get Spectators team from the database
        val (team, name) = CasualBot.database.getDiscordTeam("Spectator") to name
        val spectators = EmbedUtil.currentMembers(team!!)

        val message = buildString {
            append("**Registered Players:**\n\n")
            append(formattedTeams)
            append("\n\n")
            append("**Spectators:**\n$spectators") // Append Spectators at the end
        }

        loading.replace(message).queue()
    }

    private fun formatTeam(team: JsonElement): String {
        val teamId = team.jsonObject["id"]!!.jsonPrimitive.content
        val players = team.jsonObject["players"]!!.jsonArray

        val formattedPlayers = if (players.isNotEmpty()) {
            players.joinToString("\n") { player ->
                "- ${player.jsonObject["username"]!!.jsonPrimitive.content}"
            }
        } else {
            "No players in this team."
        }

        return "**$teamId:**\n$formattedPlayers"
    }


    private suspend fun syncTeams(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val data = loadTeams()
        val teams = data["teams"]?.jsonArray ?: return

        val playerAssignments = mutableListOf<Pair<DiscordPlayer, DiscordTeam>>()

        for (team in teams) {
            val teamName = team.jsonObject["id"]!!.jsonPrimitive.content
            val dbTeam = CasualBot.database.getDiscordTeam(teamName)

            if (dbTeam == null) {
                loading.replace(EmbedUtil.somethingWentWrongEmbed("Could not find team: $teamName")).queue()
                continue
            }

            val players = team.jsonObject["players"]!!.jsonArray
            for (playerJson in players) {
                val username = playerJson.jsonObject["username"]!!.jsonPrimitive.content
                val player = CasualBot.database.getOrCreateDiscordPlayer(username)

                if (player == null) {
                    loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid player: $username")).queue()
                    continue
                }

                playerAssignments.add(player to dbTeam)

                // Please don't crash the db xD
                delay(800)
            }
        }

        CasualBot.database.transaction {
            for ((player, dbTeam) in playerAssignments) {
                player.team = dbTeam
            }
        }

        delay(800)

        loading.replace("Teams have been successfully synced!").queue()
    }

    private fun calculatePlayersPerTeam(maxPlayers: Int): Int {
        return when {
            maxPlayers <= 32 -> 2
            maxPlayers <= 48 -> 3
            maxPlayers <= 52 -> 4
            else -> 5
        }
    }
}
