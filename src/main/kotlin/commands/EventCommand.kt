package commands

import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import util.CommandUtil
import util.EmbedUtil
import util.EventUtil
import java.util.*
import java.util.concurrent.Executors

class EventCommand : AbstractCommand() {
    private val adminOnly = setOf("create", "edit", "delete")

    override val name = "event"

    override fun getDescription() = "Create, edit, or delete events"

    override fun buildCommand(command: SlashCommandData) {

        command.subcommand("create", "Creates a new event") {
            option<String>("name", "The event name", true) {
                choice("UHC", "UHC")
            }
            option<String>("description", "The event description", true) {
                choice("Regular UHC", "regular")
            }
            option<String>("date", "The event date", true)
            option<String>("time", "The event time", true) {
                choice("12PM (Noon) [EST]", "12PM")
            }
        }

        command.subcommand("delete", "Deletes an event")

        command.subcommand("info", "Shows information about the current event")

    }

    override fun onCommand(event: GenericCommandInteractionEvent) {

        if (event.subcommandName in adminOnly && !CommandUtil.isMemberAdmin(event)) {
            event.replyEmbeds(EmbedUtil.noPermission()).queue()
            return
        }

        when (event.subcommandName) {
            "create" -> {
                val name = event.getOption("name")?.asString ?: return
                var description: String = ""
                when (event.getOption("description")?.asString) {
                    "regular" -> {
                        description =
                            "A regular server vs. server UHC Event. Teams are now open to be created. Max of 5 players per team. Current Version: `1.20.6`."
                    }
                }

                val date = event.getOption("date")?.asString ?: return
                val time = event.getOption("time")?.asString ?: return

                val channel: TextChannel = event.jda.getTextChannelById(event.channelId.toString()) ?: return

                if (EventUtil.verifyEventDetails(date, time) != "") {
                    event.reply(EventUtil.verifyEventDetails(date, time)).queue()
                    return
                }

                event.reply(EventUtil.confirmEvent(channel, name, description, date, time)).queue()

                channel.sendMessage("You have 10 seconds to confirm the information above is correct by typing `yes` or `no`.")
                    .queue {
                        Executors.newScheduledThreadPool(1).schedule({
                            val responses = channel.history.retrievePast(1).complete()
                            val response = responses.filter { it.author.id != event.jda.selfUser.id }

                            if (response.isEmpty()) {
                                return@schedule channel.sendMessage("Times up! Event creation has been cancelled.")
                                    .queue()
                            }

                            if (response.first().contentRaw.lowercase(Locale.getDefault()) == "yes") {
                                EventUtil.createEvent(event.jda, name, description, date, time)
                                return@schedule channel.sendMessage("The event is now being created!").queue()
                            } else if (response.first().contentRaw.lowercase(Locale.getDefault()) == "no") {
                                return@schedule channel.sendMessage("Event creation has been cancelled.").queue()
                            }
                        }, 10, java.util.concurrent.TimeUnit.SECONDS)
                    }
            }

            "delete" -> {
                if (!EventUtil.isThereAnEventAlready()) {
                    event.reply("There is no event to delete.").queue()
                    return
                }

                EventUtil.deleteEvent(event.jda)
                return event.reply("The event has been deleted.").queue()
            }

            "info" -> {
                event.replyEmbeds(EventUtil.getEventInfo()).queue()
            }
        }
    }
}
