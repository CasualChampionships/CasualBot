package util

import dev.minn.jda.ktx.messages.Embed
import net.dv8tion.jda.api.entities.MessageEmbed
import util.Util.capitaliseAll
import java.time.Instant
import java.time.temporal.TemporalAccessor
import java.util.*

object EmbedUtil {
    private const val UHC_LOGO = "https://cdn.discordapp.com/attachments/775083888602513439/804920103850344478/UHC_icon.png"

    fun getPlayerHead(username: String): String {
        return "https://visage.surgeplay.com/bust/${CommandUtil.MOJANK.getUUIDOfUsername(username)}"
    }

    fun getPlayerBody(username: String, size: Int): String {
        return "https://visage.surgeplay.com/full/$size/${CommandUtil.MOJANK.getUUIDOfUsername(username)}"
    }

    fun playerTakenEmbed(
        username: String,
        server: String,
        members: List<String>?,
        logo: String?,
        colour: Int
    ): MessageEmbed {
        return Embed {
            title = "$username is already on $server's team"
            color = colour
            description = "**Current Team**\n${members?.joinToString("\n")}"
            thumbnail = logo
        }
    }

    fun playerNotInTeamEmbed(
        username: String,
        server: String,
        members: List<String>,
        colour: Int
    ): MessageEmbed {
        return Embed {
            title = "$username is not on $server's team"
            color = colour
            description = "**Current Team**\n${members.joinToString("\n")}"
        }
    }

    fun addPlayerSuccessEmbed(
        username: String,
        server: String,
        members: List<String>,
        colour: Int
    ): MessageEmbed {
        return Embed {
            title = "Added $username to $server's team"
            color = colour
            description = "**Current Team**\n$username\n${members.joinToString("\n")}"
            thumbnail = getPlayerHead(username)
        }
    }

    fun removePlayerSuccessEmbed(
        username: String,
        server: String,
        members: List<String>,
        logo: String?,
        colour: Int
    ): MessageEmbed {
        return Embed {
            title = "Removed $username from $server's team"
            color = colour
            description = "**Current Team**\n${members.joinToString("\n")}"
            thumbnail = logo
        }
    }

    fun fullTeamEmbed(
        server: String,
        members: List<String>,
        logo: String?,
        colour: Int
    ): MessageEmbed {
        return Embed {
            title = "$server's team is full"
            color = colour
            description = "**Current Team**\n${members.joinToString("\n")}"
            thumbnail = logo
        }
    }

    fun getTeamInfoEmbed(
        server: String,
        members: List<String>?,
        logo: String?,
        colour: Int
    ): MessageEmbed {
        return Embed {
            title = "Info for $server"
            color = colour
            description = "**Current Team**\n${members?.joinToString("\n")}"
            thumbnail = logo
        }
    }

    fun playerStatsEmbed(username: String, imageName: String): MessageEmbed {
        return Embed {
            title = "$username's Stats"
            color = 0x7ED6DF
            image = "attachment://$imageName"
            timestamp = Instant.now()
            footer {
                name = "UHC Scoreboard"
                iconUrl = UHC_LOGO
            }
        }
    }

    fun scoreboardEmbed(stat: String, imageName: String): MessageEmbed {
        val capitalised = stat.capitaliseAll()
        return Embed {
            title = "$capitalised Scoreboard"
            color = 0x7ED6DF
            image = "attachment://$imageName"
            timestamp = Instant.now()
            footer {
                name = "UHC Scoreboard"
                iconUrl = UHC_LOGO
            }
        }
    }

    fun noStatsEmbed(username: String): MessageEmbed {
        return Embed {
            title = "No Statistics Found"
            description = "Player $username does not have any recorded statistics"
            color = 0xFF
        }
    }

    fun somethingWentWrongEmbed(message: String): MessageEmbed {
        return Embed {
            title = "Something went wrong?"
            color = 0xFF
            description = message
        }
    }
}