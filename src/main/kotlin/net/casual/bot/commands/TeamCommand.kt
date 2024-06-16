package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.casual.MinecraftColor
import net.casual.bot.CasualBot
import net.casual.bot.util.CommandUtils
import net.casual.bot.util.CommandUtils.canModifyRole
import net.casual.bot.util.CommandUtils.isAdministrator
import net.casual.bot.util.DatabaseUtils.getOrCreateDiscordPlayer
import net.casual.bot.util.EmbedUtil
import net.casual.bot.util.impl.LoadingMessage
import net.casual.database.DiscordTeam
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

object TeamCommand: Command {
    private val admin = setOf("create", "delete", "setlogo", "setcolour", "clearall")

    override val name = "team"
    override val description = "Used for creating and deleting teams"


    override fun build(command: SlashCommandData) {
        val teams = CasualBot.database.getDiscordTeams()

        command.subcommand("create", "Creates a new team") {
            option<String>("name", "The team name", true)
            option<Role>("role", "The team role", true)
            option<TextChannel>("channel", "The team channel", false)
        }
        command.subcommand("delete", "Deletes a team") {
            CommandUtils.addTeamArgument(this, teams)
        }
        command.subcommand("setlogo", "Sets the logo for a team") {
            CommandUtils.addTeamArgument(this, teams)
            option<String>("url", "The url for the logo", true)
        }
        command.subcommand("setcolor", "Sets the color for a team") {
            CommandUtils.addTeamArgument(this, teams)
            option<String>("color", "The color", true) {
                for (color in MinecraftColor.entries) {
                    choice(color.formatted, color.name)
                }
            }
        }
        command.subcommand("clear", "Clears all the players from a team") {
            CommandUtils.addTeamArgument(this, teams)
        }
        command.subcommand("clearall", "Clears all the players from all teams")
        command.subcommand("add", "adds a player to a specified team") {
            CommandUtils.addTeamArgument(this, teams)
            CommandUtils.addPlayerArgument(this)
        }
        command.subcommand("remove", "Removes a player from a specified team") {
            CommandUtils.addTeamArgument(this, teams)
            CommandUtils.addPlayerArgument(this)
        }
        command.subcommand("info", "Get a team's current information") {
            CommandUtils.addTeamArgument(this, teams)
        }
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        if (command.subcommandName in admin && !command.isAdministrator()) {
            loading.replace(EmbedUtil.noPermission()).queue()
            return
        }
        when (command.subcommandName) {
            "create" -> createTeam(command, loading)
            "delete" -> deleteTeam(command, loading)
            "setlogo" -> setTeamLogo(command, loading)
            "setcolor" -> setTeamColour(command, loading)
            "clear" -> clearTeamPlayers(command, loading)
            "clearall" -> clearAllTeamPlayers(loading)
            "add" -> addPlayerToTeam(command, loading)
            "remove" -> removePlayerFromTeam(command, loading)
            "info" -> displayTeamInfo(command, loading)
        }
    }

    private suspend fun createTeam(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val teamName = event.getOption<String>("name")!!
        val role = event.getOption<Role>("role")!!
        val channel = event.getOption<TextChannel>("channel")

        val team = CasualBot.database.getDiscordTeam(teamName)
        if (team != null) {
            loading.replace("Team with name $teamName already exists!").queue()
            return
        }

        CasualBot.database.transaction {
            DiscordTeam.new {
                name = teamName
                prefix = teamName
                logo = null
                color = MinecraftColor.WHITE
                wins = 0
                roleId = role.idLong
                channelId = channel?.idLong
            }
        }

        loading.replace("Successfully created team: $teamName").queue()
        CasualBot.reloadCommands()
    }

    private suspend fun deleteTeam(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid team: $name!")).queue()
            return
        }

        CasualBot.database.transaction {
            team.delete()
        }
        loading.replace("Successfully delete team: $name").queue()
        CasualBot.reloadCommands()
    }

    private suspend fun setTeamLogo(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid team: $name!")).queue()
            return
        }

        val url = event.getOption<String>("url")!!
        CasualBot.database.transaction {
            team.logo = url
        }

        loading.replace("Successfully updated logo").queue()
    }

    private suspend fun setTeamColour(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid team: $name!")).queue()
            return
        }

        val color = enumValueOf<MinecraftColor>(event.getOption<String>("color")!!)

        CasualBot.database.transaction {
            team.color = color
        }

        loading.replace("Successfully updated colour").queue()
    }

    private suspend fun clearTeamPlayers(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid team: $name!")).queue()
            return
        }

        if (!event.canModifyRole(team.roleId)) {
            loading.replace(EmbedUtil.noPermission()).queue()
            return
        }

        CasualBot.database.transaction {
            for (player in team.players) {
                player.team = null
            }
        }
        // TODO: Embed?
        loading.replace("Successfully cleared $name").queue()
    }

    private suspend fun clearAllTeamPlayers(loading: LoadingMessage) {
        val players = CasualBot.database.getDiscordPlayers()
        CasualBot.database.transaction {
            for (player in players) {
                player.team = null
            }
        }
        loading.replace("Successfully cleared all teams").queue()
    }

    private suspend fun removePlayerFromTeam(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid team: $name!")).queue()
            return
        }

        if (!event.canModifyRole(team.roleId)) {
            loading.replace(EmbedUtil.noPermission()).queue()
            return
        }

        val username = event.getOption<String>("username")!!

        val player = CasualBot.database.getOrCreateDiscordPlayer(username)
        if (player == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid player: $username")).queue()
            return
        }

        val existing = CasualBot.database.transaction { player.team }
        if (existing?.id?.value != team.id.value) {
            loading.replace(EmbedUtil.playerNotInTeamEmbed(player, team)).queue()
            return
        }
        CasualBot.database.transaction {
            player.team = null
        }

        loading.replace(EmbedUtil.removePlayerSuccessEmbed(player, team)).queue()
    }

    private suspend fun addPlayerToTeam(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid team: $name!")).queue()
            return
        }

        if (!event.canModifyRole(team.roleId)) {
            loading.replace(EmbedUtil.noPermission()).queue()
            return
        }


        if (CasualBot.database.transaction { team.players.count() } >= 5) {
            loading.replace(EmbedUtil.fullTeamEmbed(team)).queue()
            return
        }

        val username = event.getOption<String>("username")!!

        val player = CasualBot.database.getOrCreateDiscordPlayer(username)
        if (player == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid player: $username")).queue()
            return
        }
        val existingTeam = CasualBot.database.transaction { player.team }
        if (existingTeam != null) {
            loading.replace(EmbedUtil.playerTakenEmbed(player, existingTeam)).queue()
            return
        }

        CasualBot.database.transaction {
            player.team = team
        }
        loading.replace(EmbedUtil.addPlayerSuccessEmbed(player, team)).queue()
    }

    private suspend fun displayTeamInfo(event: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val (team, name) = CommandUtils.getTeam(event)
        if (team == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Invalid team: $name!")).queue()
            return
        }

        loading.replace(EmbedUtil.getTeamInfoEmbed(team)).queue()
    }
}