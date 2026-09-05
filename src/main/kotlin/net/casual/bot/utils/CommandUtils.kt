package net.casual.bot.utils

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.components.getOption
import me.senseiwells.mojank.SimpleMojankProfile
import net.casual.bot.CasualBot
import net.casual.bot.database.EventMode
import net.casual.bot.event.EventService
import net.casual.bot.utils.DatabaseUtils.getOrCreateDiscordPlayer
import net.casual.bot.utils.impl.LoadingMessage
import net.casual.database.DiscordPlayer
import net.casual.database.DiscordTeam
import net.casual.database.Event
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

object CommandUtils {
    const val MAX_CHOICES = 25

    fun GenericCommandInteractionEvent.isAdministrator(): Boolean {
        val user = member ?: return false
        return user.hasPermission(Permission.ADMINISTRATOR)
    }

    fun GenericCommandInteractionEvent.isOrganizer(): Boolean {
        val user = member ?: return false
        return isAdministrator() || user.roles.any { it.idLong == CasualBot.env.organizerId }
    }

    fun GenericCommandInteractionEvent.canModifyTeam(team: DiscordTeam): Boolean {
        if (isOrganizer()) {
            return true
        }
        if (EventService.activeEvent()?.mode == EventMode.Manual) {
            return true
        }
        val roleId = team.roleId ?: return false
        val user = member ?: return false
        return user.roles.any { it.idLong == roleId }
    }

    fun addTeamOption(data: SubcommandData, required: Boolean = true) {
        data.option<String>("team", "The team name", required, autocomplete = true)
    }

    fun addPlayerOption(data: SubcommandData, required: Boolean = true) {
        data.option<String>("username", "The player's Minecraft username", required)
    }

    fun addUserOption(data: SubcommandData) {
        data.option<User>("user", "The player's Discord account, so they can manage their own registration")
    }

    fun addEventOption(data: SubcommandData, events: List<Event>) {
        data.option<String>("event", "The event you want to display the scoreboard for") {
            for (event in events) {
                choice(event.name.toDisplayName(), event.name)
            }
        }
    }

    fun GenericCommandInteractionEvent.requireOption(option: String): String {
        return getOption<String>(option) ?: throw IllegalArgumentException("Unknown option $option!")
    }

    fun getTeam(event: GenericCommandInteractionEvent, option: String = "team"): Pair<DiscordTeam?, String> {
        val name = event.requireOption(option)
        return CasualBot.database.getDiscordTeam(name) to name
    }

    suspend fun getPlayer(
        event: GenericCommandInteractionEvent,
        loading: LoadingMessage,
        option: String = "username"
    ): DiscordPlayer? {
        val username = event.requireOption(option)
        val player = CasualBot.database.getOrCreateDiscordPlayer(username)
        if (player == null) {
            loading.replace(EventEmbeds.unknownUsername(username))
        }
        return player
    }

    fun teamChoices(event: CommandAutoCompleteInteractionEvent): List<Choice> {
        val query = event.focusedOption.value.lowercase()
        return CasualBot.database.getDiscordTeams().asSequence()
            .map { it.name }
            .filter { it.lowercase().startsWith(query) }
            .sorted()
            .take(this.MAX_CHOICES)
            .map { Choice(it, it) }
            .toList()
    }

    suspend fun completeTeams(event: CommandAutoCompleteInteractionEvent) {
        if (event.focusedOption.name == "team") {
            event.replyChoices(this.teamChoices(event)).await()
        }
    }

    suspend fun roleHolders(): Map<Long, Set<Long>>? {
        val guild = CasualBot.guild ?: return null
        val members = try {
            guild.loadMembers().await()
        } catch (e: Exception) {
            CasualBot.logger.warn(e) { "Could not load guild members, is the GUILD_MEMBERS intent enabled?" }
            return null
        }
        val holders = HashMap<Long, MutableSet<Long>>()
        for (member in members) {
            for (role in member.roles) {
                holders.getOrPut(role.idLong) { HashSet() }.add(member.idLong)
            }
        }
        return holders
    }

    suspend fun getMojangProfile(
        event: GenericCommandInteractionEvent,
        option: String = "username"
    ): Pair<SimpleMojankProfile?, String> {
        val username = event.requireOption(option)
        return DatabaseUtils.getSimpleMojangProfile(username).getOrNull() to username
    }
}
