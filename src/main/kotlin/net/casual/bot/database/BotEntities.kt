package net.casual.bot.database

import net.casual.database.DiscordPlayer
import net.casual.database.DiscordTeam
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class BotEvent(id: EntityID<Int>): IntEntity(id) {
    var name by BotEvents.name
    var mode by BotEvents.mode
    var state by BotEvents.state
    var teamSize by BotEvents.teamSize

    var panelChannelId by BotEvents.panelChannelId
    var panelMessageId by BotEvents.panelMessageId

    var archived by BotEvents.archived
    var createdAt by BotEvents.createdAt

    val registrations by Registration referrersOn BotRegistrations.event

    val acceptingRegistrations: Boolean
        get() = !archived && state == EventState.Open

    companion object: IntEntityClass<BotEvent>(BotEvents)
}

class Registration(id: EntityID<Int>): IntEntity(id) {
    var event by BotEvent referencedOn BotRegistrations.event
    var discordId by BotRegistrations.discordId
    var player by DiscordPlayer referencedOn BotRegistrations.player
    var team by DiscordTeam optionalReferencedOn BotRegistrations.team
    var spectating by BotRegistrations.spectating
    var registeredAt by BotRegistrations.registeredAt

    companion object: IntEntityClass<Registration>(BotRegistrations)
}

class PlayerLink(id: EntityID<Int>): IntEntity(id) {
    var discordId by BotPlayerLinks.discordId
    var player by DiscordPlayer referencedOn BotPlayerLinks.player
    var linkedAt by BotPlayerLinks.linkedAt

    companion object: IntEntityClass<PlayerLink>(BotPlayerLinks)
}
