package util

import CONFIG
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object EventUtil {

    fun isThereAnEventAlready(): Boolean {
        val event = CONFIG.event.get("name").asString
        return event != ""
    }

    fun verifyEventDetails(date: String, time: String): String {
        val dateRegex = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
        val timeRegex = Regex("^(\\d{1,2})(AM|PM)$")

        if (isThereAnEventAlready()) {
            return "There is already an event scheduled. Either edit the existing one or delete it."
        }

        if (!dateRegex.matches(date)) {
            return "The date must be formatted as YYYY-MM-DD"
        }

        if (!timeRegex.matches(time)) {
            return "The time must be formatted as HHAM or HHPM"
        }

        return ""
    }

    fun confirmEvent(channel: TextChannel, name: String, description: String, date: String, time: String): String {
        return "**Event Name:** $name\n**Event Description:** $description\n**Event Date and time:** " + convertToTimestamp(
            date,
            time
        )

    }

    private fun convertToTimestamp(date: String, time: String): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd ha")
        val timeZone = ZoneId.of("America/New_York")

        val localDateTime = LocalDateTime.parse("$date $time", formatter)
        val zonedDateTime = localDateTime.atZone(timeZone)
        val unix = zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toEpochSecond()

        return "<t:$unix:F> (<t:$unix:R>)"
    }

    fun createEvent(jda: JDA, name: String, description: String, date: String, time: String) {
        CONFIG.writeEventInfoToFile(name, description, date, time)
        CONFIG.updateEvent()
        updateStatusEmbed(jda, EmbedUtil.nextEventStatusEmbed(name, description, convertToTimestamp(date, time)))
        notifyServerChannels(
            jda,
            "An event has been scheduled! Teams can now be created. Check out <#" + CONFIG.channelIds.get("status").asLong + "> for more information."
        )
        return
    }

    fun getEventInfo(): MessageEmbed {
        if (!isThereAnEventAlready()) {
            return EmbedUtil.noEventScheduledEmbed()
        }

        return EmbedUtil.nextEventStatusEmbed(
            CONFIG.event.get("name").asString,
            CONFIG.event.get("description").asString,
            convertToTimestamp(CONFIG.event.get("date").asString, CONFIG.event.get("time").asString)
        )
    }

    fun deleteEvent(jda: JDA) {
        CONFIG.writeEventInfoToFile("", "", "", "")
        CONFIG.updateEvent()
        updateStatusEmbed(jda, EmbedUtil.noEventScheduledEmbed())
        notifyServerChannels(
            jda,
            "The scheduled event has been deleted. Check <#" + CONFIG.channelIds.get("status").asLong + "> for any updates."
        )
    }

    private fun updateStatusEmbed(jda: JDA, embed: MessageEmbed) {
        val channel = CONFIG.channelIds.get("status").asLong

        MessageUtil.firstMessage(
            jda, channel, listOf(""), listOf(embed)
        )
    }

    private fun notifyServerChannels(jda: JDA, message: String) {
        val serverChannelIds = CONFIG.serverChannelIds
        for (key in serverChannelIds.keySet()) {
            val channelId = serverChannelIds.get(key).asLong
            val channel = jda.getTextChannelById(channelId) ?: continue
            channel.sendMessage(message).queue()
        }
    }

    // Check if the event date has passed, if it has been a day since the event, change the status to no event scheduled
    fun checkIfEventHasPassed(jda: JDA) {
        if (!isThereAnEventAlready()) {
            return
        }

        val date = CONFIG.event.get("date").asString
        val time = CONFIG.event.get("time").asString

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd ha")
        val timeZone = ZoneId.of("America/New_York")

        val localDateTime = LocalDateTime.parse("$date $time", formatter)
        val zonedDateTime = localDateTime.atZone(timeZone)
        val unix = zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toEpochSecond()

        if (unix < System.currentTimeMillis() / 1000) {
            deleteEvent(jda)
        }
    }

}
