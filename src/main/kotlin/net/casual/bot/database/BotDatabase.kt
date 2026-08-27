package net.casual.bot.database

import net.casual.database.CasualDatabase
import net.casual.database.DiscordPlayer
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.SchemaUtils

object BotDatabase {
    fun CasualDatabase.initializeBotTables() {
        transaction {
            SchemaUtils.create(BotEvents, BotRegistrations, BotPlayerLinks)
        }
    }

    fun CasualDatabase.activeEvent(): BotEvent? {
        return transaction {
            BotEvent.find { BotEvents.archived eq false }
                .orderBy(BotEvents.id to SortOrder.DESC)
                .firstOrNull()
        }
    }

    fun CasualDatabase.registrationOf(event: BotEvent, discordId: Long): Registration? {
        return transaction {
            Registration.find {
                (BotRegistrations.event eq event.id) and (BotRegistrations.discordId eq discordId)
            }.singleOrNull()
        }
    }

    fun CasualDatabase.registrationOf(event: BotEvent, player: DiscordPlayer): Registration? {
        return transaction {
            Registration.find {
                (BotRegistrations.event eq event.id) and (BotRegistrations.player eq player.id)
            }.singleOrNull()
        }
    }

    fun CasualDatabase.linkedDiscordId(player: DiscordPlayer): Long? {
        return transaction {
            PlayerLink.find { BotPlayerLinks.player eq player.id }.singleOrNull()?.discordId
        }
    }

    fun CasualDatabase.linkedPlayer(discordId: Long): DiscordPlayer? {
        return transaction {
            PlayerLink.find { BotPlayerLinks.discordId eq discordId }.singleOrNull()?.player
        }
    }

    fun CasualDatabase.linkPlayer(discordId: Long, player: DiscordPlayer, now: Long): PlayerLink {
        return transaction {
            PlayerLink.find {
                (BotPlayerLinks.discordId eq discordId) or (BotPlayerLinks.player eq player.id)
            }.forEach { it.delete() }

            PlayerLink.new {
                this.discordId = discordId
                this.player = player
                this.linkedAt = now
            }
        }
    }
}
