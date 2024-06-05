package util

import BOT
import CONFIG
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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.shanerx.mojang.Mojang

object CommandUtil {
    private val COMMANDS = setOf(
        StatCommand(),
        ScoreboardCommand(),
        TeamCommand(),
        ReloadCommand(),
        EventCommand()
    ).associateBy { it.name }

    val MOJANK = Mojang().connect()

    fun loadCommands(jda: JDA) {
        for (command in jda.guilds.first().retrieveCommands().complete()) {
            command.delete().queue()
        }

        jda.updateCommands {
            for (command in COMMANDS.values) {
                slash(command.name, command.getDescription()) {
                    command.buildCommand(this)
                }
            }
        }.queue()
    }

    fun onCommand(event: GenericCommandInteractionEvent) {
        COMMANDS[event.name]?.onCommand(event)
    }

    fun isMemberAdmin(event: GenericCommandInteractionEvent): Boolean {
        val user = event.member ?: return false
        for (role in user.roles) {
            if (role.hasPermission(Permission.ADMINISTRATOR)) {
                return true
            }
        }
        // event.reply("You cannot use the command ${event.commandString}")
        return false
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

    fun getNameAndUUID(username: String): Pair<String, String>? {
        return try {
            val uuid = MOJANK.getUUIDOfUsername(username)
            MOJANK.getPlayerProfile(uuid).username to addDashes(uuid)
        } catch (e: RuntimeException) {
            null
        }
    }

    fun Role.isServerRole(): Boolean = !CONFIG.nonTeams.contains(name) && isHoisted

    fun SlashCommandData.addServerArgument() {
        option<String>("server", "The specified server", true) {
            for (role in BOT.db.getTeams().values) {
                choice(role, role)
            }
        }
    }

    fun SubcommandData.addServerArgument() {
        option<String>("server", "The specified server", true) {
            for (role in BOT.db.getTeams().keys) {
                choice(role, role)
            }
        }
    }

    fun SlashCommandData.addPlayerArgument() {
        option<String>("username", "The player's Minecraft username", true)
    }

    fun SubcommandData.addPlayerArgument() {
        option<String>("username", "The player's Minecraft username", true)
    }

    inline fun GenericCommandInteractionEvent.getServerOr(invalid: (String?) -> Nothing): Pair<String, Role> {
        val option = getOption<String>("server")!!
        val regex = if (option.matches(Regex("^\\d+$"))) "_\\d+$" else "\\d+$"
        val roleName = option.replace(Regex(regex), "")
        val role = guild?.getRolesByName(roleName, true)?.firstOrNull()
        if (role == null || !role.isServerRole()) {
            invalid(option)
        }
        return option to role
    }

    inline fun GenericCommandInteractionEvent.getPlayerOr(invalid: (String) -> Nothing): Pair<String, String> {
        val username = getOption<String>("username")!!
        val name = getNameAndUUID(username)
        name ?: invalid(username)
        return name
    }

    private fun addDashes(uuid: String): String {
        val builder = StringBuilder(uuid)
        builder.insert(20, '-')
        builder.insert(16, '-')
        builder.insert(12, '-')
        builder.insert(8, '-')
        return builder.toString()
    }
}