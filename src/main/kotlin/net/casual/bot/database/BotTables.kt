package net.casual.bot.database

import net.casual.database.DiscordPlayers
import net.casual.database.DiscordTeams
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

enum class EventMode {
    Servers,
    Randomized,
    Manual
}

enum class EventState {
    Open,
    Closed,
    Locked
}

object BotEvents: IntIdTable() {
    val name = varchar("name", 64)
    val mode = enumerationByName<EventMode>("mode", 16)
    val state = enumerationByName<EventState>("state", 16)
    val teamSize = integer("team_size")

    val panelChannelId = long("panel_channel_id").nullable()
    val panelMessageId = long("panel_message_id").nullable()

    val archived = bool("archived").default(false)
    val createdAt = long("created_at")
    val lockedAt = long("locked_at").nullable()
}

object BotRegistrations: IntIdTable() {
    val event = reference("event", BotEvents, onDelete = ReferenceOption.CASCADE)
    val discordId = long("discord_id").nullable()
    val player = reference("player", DiscordPlayers, onDelete = ReferenceOption.CASCADE)
    val team = optReference("team", DiscordTeams, onDelete = ReferenceOption.SET_NULL)

    val spectating = bool("spectating").default(false)
    val registeredAt = long("registered_at")

    init {
        this.uniqueIndex(this.event, this.discordId)
        this.uniqueIndex(this.event, this.player)
    }
}

object BotPlayerLinks: IntIdTable() {
    val discordId = long("discord_id").uniqueIndex()
    val player = reference("player", DiscordPlayers, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val linkedAt = long("linked_at")
}
