package util

import dev.minn.jda.ktx.messages.Embed
import net.dv8tion.jda.api.entities.MessageEmbed

object EmbedUtil {
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

    fun winEmbed(winList: List<String>) {

    }

    fun somethingWentWrongEmbed(message: String): MessageEmbed {
        return Embed {
            title = "Something went wrong?"
            color = 0xFF
            description = message
        }
    }
}