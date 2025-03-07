package net.casual.bot.util

import dev.minn.jda.ktx.messages.Embed
import net.casual.bot.CasualBot
import net.casual.database.DiscordPlayer
import net.casual.database.DiscordTeam
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import java.util.Comparator
import java.util.UUID

// TODO: Redo some of these embeds
object EmbedUtil {
    private fun getPlayerHead(player: DiscordPlayer): String {
        return "https://visage.surgeplay.com/bust/${player.id}"
    }

    fun getPlayerBody(uuid: UUID, size: Int): String {
        return "https://visage.surgeplay.com/full/$size/${uuid}"
    }

    fun playerTakenEmbed(player: DiscordPlayer, team: DiscordTeam): MessageEmbed {
        return Embed {
            title = "${player.name} is already on ${team.name}'s team"
            color = team.color
            description = currentMembers(team)
            thumbnail = team.logo
        }
    }

    fun playerNotInTeamEmbed(player: DiscordPlayer, team: DiscordTeam): MessageEmbed {
        return Embed {
            title = "${player.name} is not on ${team.name}'s team"
            color = team.color
            description = currentMembers(team)
        }
    }

    fun addPlayerSuccessEmbed(player: DiscordPlayer, team: DiscordTeam): MessageEmbed {
        return Embed {
            title = "Added ${player.name} to ${team.name}'s team"
            color = team.color
            description = currentMembers(team)
            thumbnail = getPlayerHead(player)
        }
    }

    fun removePlayerSuccessEmbed(player: DiscordPlayer, team: DiscordTeam): MessageEmbed {
        return Embed {
            title = "Removed ${player.name} from ${team.name}'s team"
            color = team.color
            description = currentMembers(team)
            thumbnail = team.logo
        }
    }

    fun fullTeamEmbed(team: DiscordTeam): MessageEmbed {
        return Embed {
            title = "${team.name}'s team is full"
            color = team.color
            description = currentMembers(team)
            thumbnail = team.logo
        }
    }

    fun getTeamInfoEmbed(team: DiscordTeam): MessageEmbed {
        return Embed {
            title = "Info for ${team.name}"
            color = team.color
            description = currentMembers(team)
            thumbnail = team.logo
        }
    }


    fun noStatsEmbed(username: String): MessageEmbed {
        return Embed {
            title = "No Statistics Found"
            description = "Player $username does not have any recorded statistics"
            color = 0xFF9494
        }
    }

    fun somethingWentWrongEmbed(message: String): MessageEmbed {
        return Embed {
            title = "Something went wrong?"
            color = 0xFF9494
            description = message
        }
    }

    fun noPermission(): MessageEmbed {
        return Embed {
            title = "You do not have permission to do this action"
            color = 0xFF9494
        }
    }

    public fun currentMembers(members: DiscordTeam): String {
        val players = CasualBot.database.transaction {
            members.players.sortedWith(Comparator.comparing { it.name })
        }
        if (players.isEmpty()) {
            return "**No Members**"
        }
        return "**Current Team**\n${
            MarkdownSanitizer.sanitize(
                players.joinToString("\n") { it.name },
                MarkdownSanitizer.SanitizationStrategy.ESCAPE
            )
        }"
    }

    fun eventJoinSuccessEmbed(username: String, remainingSpots: Int): MessageEmbed {
        return Embed {
            title = "Successfully joined the event, $username!"
            color = 5832620
            description = "Spots Remaining: $remainingSpots"
        }
    }

    fun eventLeaveSuccessEmbed(username: String, remainingSpots: Int): MessageEmbed {
        return Embed {
            title = "Successfully left the event, $username!"
            color = 5832620
            description = "Spots Remaining: $remainingSpots"
        }
    }

    fun eventLeaveFailureEmbed(username: String, reason: String): MessageEmbed {
        return Embed {
            title = "Failure to leave the event, $username!"
            color = 16730955
            description = reason
        }
    }

    fun eventJoinFailure(username: String, reason: String): MessageEmbed {
        return Embed {
            title = "Failure to join the event, $username!"
            color = 16730955
            description = reason
        }
    }

    fun eventFullEmbed(username: String): MessageEmbed {
        return Embed {
            title = "Failure to join the event, $username!"
            color = 16730955
            description = "No available spots left to join."
        }
    }


    fun nextEventEmbed(name: String, time: Long, desc: String): MessageEmbed {
        return Embed {
            description =
                "# $name \n* Time and Date: <t:$time:F> (<t:$time:R>) \n$desc\n\nThe discord bot is now open and online to start creating teams or registering for the event! "
            color = 0xAA9BFF
        }
    }

    fun noEventScheduledEmbed(): MessageEmbed {
        return Embed {
            description = "# No Event is Currently Scheduled \nThere is usually only one event every month. Check back later for updates!"
            color = 0xFF4B4B
        }
    }
}
