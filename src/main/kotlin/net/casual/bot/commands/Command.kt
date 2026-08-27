package net.casual.bot.commands

import net.casual.bot.utils.impl.LoadingMessage
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

interface Command {
    val name: String
    val description: String

    val ephemeral: Boolean get() = false

    fun build(command: SlashCommandData)

    suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage)

    suspend fun autocomplete(event: CommandAutoCompleteInteractionEvent) {

    }

    suspend fun modal(command: SlashCommandInteractionEvent): Boolean {
        return false
    }
}
