package net.casual.bot.util

import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.components.getOption
import me.senseiwells.mojank.SimpleMojankProfile
import net.casual.bot.CasualBot
import net.casual.database.DiscordTeam
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

object CommandUtils {
    fun GenericCommandInteractionEvent.isAdministrator(): Boolean {
        val user = this.member ?: return false
        return user.hasPermission(Permission.ADMINISTRATOR)
    }

    fun GenericCommandInteractionEvent.canModifyRole(roleId: Long): Boolean {
        val user = this.member ?: return false
        if (isAdministrator()) {
            return true
        }
        if (user.roles.any { it.idLong == roleId }) {
            return true
        }
        return false
    }

    fun addTeamArgument(data: SlashCommandData, teams: List<DiscordTeam>) {
        data.option<String>("team", "The specified team", true) {
            for (team in CasualBot.database.getDiscordTeams()) {
                choice(team.name, team.name)
            }
        }
    }

    fun addTeamArgument(data: SubcommandData, teams: List<DiscordTeam>) {
        data.option<String>("team", "The specified team", true) {
            for (team in teams) {
                choice(team.name, team.name)
            }
        }
    }

    fun getTeam(event: GenericCommandInteractionEvent, option: String = "team"): Pair<DiscordTeam?, String> {
        val name = event.getOption<String>(option) ?: throw IllegalArgumentException("Unknown option $option!")
        return CasualBot.database.getDiscordTeam(name) to name
    }

    fun addPlayerArgument(data: SlashCommandData) {
        data.option<String>("username", "The player's Minecraft username", true)
    }

    fun addPlayerArgument(data: SubcommandData) {
        data.option<String>("username", "The player's Minecraft username", true)
    }

    suspend fun getMojangProfile(
        event: GenericCommandInteractionEvent,
        option: String = "username"
    ): Pair<SimpleMojankProfile?, String> {
        val username = event.getOption<String>(option) ?: throw IllegalArgumentException("Unknown option $option!")
        return DatabaseUtils.getSimpleMojangProfile(username).getOrNull() to username
    }
}