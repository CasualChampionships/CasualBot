package util

import BOT
import CONFIG
import LOGGER
import commands.*
import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.shanerx.mojang.Mojang

object CommandUtil {
    private val COMMANDS = setOf(
        AddPlayerCommand(),
        RemovePlayerCommand(),
        TeamInfoCommand(),
        ClearTeamCommand(),
        StatCommand()
    ).associateBy { it.getName() }

    val MOJANK = Mojang().connect()

    fun loadCommands(jda: JDA) {
        jda.updateCommands {
            for (command in COMMANDS.values) {
                slash(command.getName(), command.getDescription()) {
                    command.buildCommand(this)
                }
            }
        }.queue()
    }

    fun onCommand(event: GenericCommandInteractionEvent) {
        COMMANDS[event.name]?.onCommand(event)
    }

    fun canMemberModifyTeam(event: GenericCommandInteractionEvent, server: Role): Boolean {
        val user = event.member ?: return false
        for (role in user.roles) {
            if (role.idLong == server.idLong || role.hasPermission(Permission.ADMINISTRATOR)) {
                return true
            }
        }
        event.reply("You cannot modify the team ${server.name}")
        return false
    }

    fun getCorrectName(username: String): String? {
        return try {
            val uuid = MOJANK.getUUIDOfUsername(username)
            MOJANK.getPlayerProfile(uuid).username
        } catch (e: RuntimeException) {
            null
        }
    }

    fun Role.isServerRole(): Boolean = !CONFIG.nonTeams.contains(name) && isHoisted

    fun SlashCommandData.addServerArgument() {
        option<String>("server", "The specified server", true) {
            for (role in BOT.guild.roles) {
                if (role.isServerRole()) {
                    choice(role.name, role.name)
                }
            }
        }
    }

    fun SlashCommandData.addPlayerArgument() {
        option<String>("username", "The player's Minecraft username", true)
    }

    fun GenericCommandInteractionEvent.getServer(invalid: (String) -> Unit): Role? {
        val serverName = getOption<String>("server") ?: return null
        val role = guild?.getRolesByName(serverName, true)?.first()
        role ?: invalid(serverName)
        return role
    }

    fun GenericCommandInteractionEvent.getPlayer(invalid: (String) -> Unit): String? {
        val username = getOption<String>("username") ?: return null
        val name = getCorrectName(username)
        name ?: invalid(username)
        return name
    }
}