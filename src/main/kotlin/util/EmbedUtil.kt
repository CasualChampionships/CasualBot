package util

import CONFIG
import dev.minn.jda.ktx.messages.Embed
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import util.Util.capitaliseAll
import java.time.Instant

object EmbedUtil {
    private const val UHC_LOGO =
        "https://cdn.discordapp.com/attachments/775083888602513439/804920103850344478/UHC_icon.png"

    private fun getPlayerHead(username: String): String {
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
            description = currentMembers(members)
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
            description = currentMembers(members)
        }
    }

    fun addPlayerSuccessEmbed(
        username: String,
        server: String,
        members: List<String>,
        colour: Int
    ): MessageEmbed {
        val updated = members.toMutableList()
        updated.add(username)
        return Embed {
            title = "Added $username to $server's team"
            color = colour
            description = currentMembers(updated)
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
            description = currentMembers(members)
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
            description = currentMembers(members)
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
            description = currentMembers(members)
            thumbnail = logo
        }
    }

    fun playerStatsEmbed(username: String, imageName: String, lifetime: Boolean): MessageEmbed {
        val modifier = if (lifetime) "Lifetime" else "Latest"
        return Embed {
            title = "$username's $modifier Stats"
            color = 0x7ED6DF
            image = "attachment://$imageName"
            timestamp = Instant.now()
            footer {
                name = "UHC Stats"
                iconUrl = UHC_LOGO
            }
        }
    }

    fun playerAdvancementsEmbed(imageName: String): MessageEmbed {
        return Embed {
            color = 0x7ED6DF
            image = "attachment://$imageName"
            timestamp = Instant.now()
            footer {
                name = "UHC Advancements"
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

    fun customEmbed(key: String): Pair<MessageEmbed, Long> {
        val embed = CONFIG.embeds[key]
        embed ?: return Pair(somethingWentWrongEmbed("Embed not found..."), 1)
        return Pair(embed.toEmbed(), embed.channelId)
    }

    fun winsEmbed(teams: Map<String, String>): MessageEmbed {
        return Embed {
            title = "Wins"
            description = teams.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            color = 0x7ED6DF
            thumbnail = UHC_LOGO
        }
    }

    private fun currentMembers(members: List<String>?): String {
        if (members.isNullOrEmpty()) {
            return "**No Members**"
        }
        return "**Current Team**\n${
            MarkdownSanitizer.sanitize(
                members.joinToString("\n"),
                MarkdownSanitizer.SanitizationStrategy.ESCAPE
            )
        }"
    }

    fun nextEventStatusEmbed(name: String, desc: String, timestamp: String): MessageEmbed {
        return Embed {
            title = "Next Event: $name"
            description = "$desc\n\nThe time and date of this event will be: \n$timestamp"
            color = 0x206694
            thumbnail = UHC_LOGO
        }
    }

    fun noEventScheduledEmbed(): MessageEmbed {
        return Embed {
            title = "No Events Scheduled"
            description = "There is currently no events scheduled. Check back later for updates!"
            color = 0x206694
            thumbnail = UHC_LOGO
        }
    }
}
