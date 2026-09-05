package net.casual.bot.panel

import dev.minn.jda.ktx.coroutines.await
import net.casual.bot.CasualBot
import net.casual.bot.database.BotDatabase.linkedPlayer
import net.casual.bot.database.BotDatabase.registrationOf
import net.casual.bot.database.BotEvent
import net.casual.bot.event.EventService
import net.casual.bot.utils.EventEmbeds
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback

object PanelInteractions {
    suspend fun onButton(interaction: ButtonInteractionEvent) {
        val parsed = PanelComponents.parseButton(interaction.componentId)
        if (parsed == null) {
            interaction.replyEmbeds(this.stalePanel()).setEphemeral(true).await()
            return
        }
        val (action, eventId) = parsed
        val event = this.resolveEvent(interaction, eventId) ?: return
        val discordId = interaction.user.idLong

        if (action != PanelAction.Unregister && this.needsUsername(event, discordId)) {
            interaction.replyModal(PanelComponents.usernameModal(action, eventId)).await()
            return
        }

        val hook = interaction.deferReply(true).await()
        val roleIds = interaction.member?.roles?.map { it.idLong } ?: listOf()

        val embed = when (action) {
            PanelAction.Register -> EventEmbeds.register(EventService.register(discordId, roleIds))
            PanelAction.Unregister -> EventEmbeds.leave(EventService.leave(discordId))
            PanelAction.Spectate -> EventEmbeds.spectate(EventService.spectate(discordId))
        }

        hook.editOriginalEmbeds(embed).await()
        EventService.rosterChanged(event)
    }

    suspend fun onModal(interaction: ModalInteractionEvent) {
        val (action, eventId) = PanelComponents.parseModal(interaction.modalId) ?: return
        val event = this.resolveEvent(interaction, eventId) ?: return

        val hook = interaction.deferReply(true).await()
        val username = interaction.getValue(PanelComponents.USERNAME_FIELD)?.asString?.trim()
        if (username.isNullOrEmpty()) {
            hook.editOriginalEmbeds(
                EventEmbeds.failure("We didn't get a username", "Try again and type your Minecraft name.")
            ).await()
            return
        }

        val discordId = interaction.user.idLong
        val roleIds = interaction.member?.roles?.map { it.idLong } ?: listOf()

        val embed = when (action) {
            PanelAction.Register -> EventEmbeds.register(EventService.register(discordId, roleIds, username))
            PanelAction.Spectate -> EventEmbeds.spectate(EventService.spectate(discordId, username))
            PanelAction.Unregister -> EventEmbeds.leave(EventService.leave(discordId))
        }

        hook.editOriginalEmbeds(embed).await()
        EventService.rosterChanged(event)
    }

    private suspend fun resolveEvent(interaction: IReplyCallback, eventId: Int): BotEvent? {
        val event = EventService.activeEvent()
        if (event == null || event.id.value != eventId) {
            interaction.replyEmbeds(this.stalePanel()).setEphemeral(true).await()
            return null
        }
        return event
    }

    private fun stalePanel(): MessageEmbed {
        return EventEmbeds.failure(
            "This panel is out of date",
            "It belongs to an event that has already finished. Look for the current one, " +
                "or ask an organizer to post a new panel."
        )
    }

    private fun needsUsername(event: BotEvent, discordId: Long): Boolean {
        if (CasualBot.database.registrationOf(event, discordId) != null) {
            return false
        }
        return CasualBot.database.linkedPlayer(discordId) == null
    }
}
