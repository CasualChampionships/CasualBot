package net.casual.bot.commands

import net.casual.bot.util.impl.LoadingMessage
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

interface Command {
    val name: String
    val description: String

    fun build(command: SlashCommandData)

    suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage)
}