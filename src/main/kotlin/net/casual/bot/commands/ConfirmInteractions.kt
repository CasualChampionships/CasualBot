package net.casual.bot.commands

import dev.minn.jda.ktx.coroutines.await
import net.casual.bot.CasualBot
import net.casual.bot.event.EventService
import net.casual.bot.panel.RegistrationPanel
import net.casual.bot.utils.EventEmbeds
import net.casual.database.DiscordTeam
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

object ConfirmInteractions {
    suspend fun onButton(interaction: ButtonInteractionEvent): Boolean {
        val confirmation = ConfirmComponents.parse(interaction.componentId) ?: return false

        if (interaction.user.idLong != confirmation.userId) {
            interaction.replyEmbeds(
                EventEmbeds.failure("Invalid action", "This wasn't yours to interact with!")
            ).setEphemeral(true).await()
            return true
        }

        if (confirmation.choice == ConfirmChoice.Cancel) {
            interaction.editMessageEmbeds(EventEmbeds.notice("Cancelled", "Nothing was changed"))
                .setComponents()
                .await()
            return true
        }

        when (confirmation.action) {
            ConfirmComponents.TEAM_DELETE -> this.deleteTeam(interaction, confirmation.target)
            ConfirmComponents.EVENT_END -> this.endEvent(interaction, confirmation.target)
            ConfirmComponents.EVENT_ALLOCATE -> this.allocate(interaction, confirmation.choice)
        }
        return true
    }

    private suspend fun deleteTeam(interaction: ButtonInteractionEvent, target: String) {
        val id = target.toIntOrNull()
        val team = id?.let { CasualBot.database.transaction { DiscordTeam.findById(it) } }
        if (team == null) {
            this.replace(interaction, EventEmbeds.failure("That team is already gone", "Nothing to delete"))
            return
        }

        val name = team.name
        CasualBot.database.transaction { team.delete() }
        this.replace(interaction, EventEmbeds.notice("Deleted ${EventEmbeds.escape(name)}", "The team has been removed"))
        EventService.activeEvent()?.let { RegistrationPanel.refresh(it) }
    }

    private suspend fun endEvent(interaction: ButtonInteractionEvent, target: String) {
        val event = EventService.activeEvent()
        if (event == null || event.id.value.toString() != target) {
            this.replace(interaction, EventEmbeds.notice("That event has already finished", "Nothing changed"))
            return
        }

        EventService.archive(event)
        RegistrationPanel.refresh(event)
        this.replace(
            interaction,
            EventEmbeds.notice(
                "Finished ${EventEmbeds.escape(event.name)}",
                "Registrations are kept, create a new event when you're ready"
            )
        )
    }

    private suspend fun allocate(interaction: ButtonInteractionEvent, choice: ConfirmChoice) {
        if (choice == ConfirmChoice.Retry) {
            val result = EventService.allocate(apply = false)
            interaction.editMessageEmbeds(EventEmbeds.allocate(result, applied = false)).await()
            return
        }

        val result = EventService.allocate(apply = true)
        this.replace(interaction, EventEmbeds.allocate(result, applied = true))
        EventService.activeEvent()?.let { RegistrationPanel.refresh(it) }
    }

    private suspend fun replace(interaction: ButtonInteractionEvent, embed: MessageEmbed) {
        interaction.editMessageEmbeds(embed).setComponents().await()
    }
}
