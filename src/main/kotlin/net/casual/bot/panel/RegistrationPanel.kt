package net.casual.bot.panel

import dev.minn.jda.ktx.coroutines.await
import net.casual.bot.CasualBot
import net.casual.bot.database.BotEvent
import net.casual.bot.event.EventService
import net.casual.bot.utils.EventEmbeds
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData

object RegistrationPanel {
    fun build(event: BotEvent): MessageCreateData {
        val summary = EventService.summarise(event)
        return MessageCreateBuilder()
            .setEmbeds(EventEmbeds.panel(summary))
            .setComponents(PanelComponents.buttons(event.id.value, event.acceptingRegistrations))
            .build()
    }

    suspend fun post(channel: GuildMessageChannel, event: BotEvent) {
        val message = channel.sendMessage(this.build(event)).await()
        CasualBot.database.transaction {
            event.panelChannelId = channel.idLong
            event.panelMessageId = message.idLong
        }
        runCatching { message.pin().await() }
    }

    suspend fun refresh(event: BotEvent) {
        val channelId = event.panelChannelId ?: return
        val messageId = event.panelMessageId ?: return
        val channel = CasualBot.jda.getChannelById(GuildMessageChannel::class.java, channelId) ?: return

        runCatching {
            channel.editMessageById(messageId, MessageEditData.fromCreateData(this.build(event))).await()
        }.onFailure {
            CasualBot.logger.warn(it) { "Could not refresh the registration panel, it may have been deleted" }
        }
    }

    suspend fun reattach() {
        val event = EventService.activeEvent() ?: return
        if (event.panelMessageId == null) {
            return
        }
        this.refresh(event)
    }
}
