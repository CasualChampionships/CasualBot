package net.casual.bot.panel

import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.modals.Modal

enum class PanelAction(val id: String) {
    Register("register"),
    Unregister("unregister"),
    Spectate("spectate");

    companion object {
        fun of(id: String): PanelAction? = entries.firstOrNull { it.id == id }
    }
}

object PanelComponents {
    private const val PREFIX = "reg"
    private const val MODAL = "username"
    const val USERNAME_FIELD = "username"

    fun buttonId(action: PanelAction, eventId: Int): String {
        return "$PREFIX:${action.id}:$eventId"
    }

    fun modalId(action: PanelAction, eventId: Int): String {
        return "$PREFIX:$MODAL:$eventId:${action.id}"
    }

    fun parseButton(id: String): Pair<PanelAction, Int>? {
        val parts = id.split(":")
        if (parts.size != 3 || parts[0] != this.PREFIX) {
            return null
        }
        val action = PanelAction.of(parts[1]) ?: return null
        val eventId = parts[2].toIntOrNull() ?: return null
        return action to eventId
    }

    fun parseModal(id: String): Pair<PanelAction, Int>? {
        val parts = id.split(":")
        if (parts.size != 4 || parts[0] != this.PREFIX || parts[1] != this.MODAL) {
            return null
        }
        val eventId = parts[2].toIntOrNull() ?: return null
        val action = PanelAction.of(parts[3]) ?: return null
        return action to eventId
    }

    fun buttons(eventId: Int, accepting: Boolean): ActionRow {
        return ActionRow.of(
            Button.success(this.buttonId(PanelAction.Register, eventId), "Register")
                .withDisabled(!accepting),
            Button.secondary(this.buttonId(PanelAction.Unregister, eventId), "Unregister")
                .withDisabled(!accepting),
            Button.secondary(this.buttonId(PanelAction.Spectate, eventId), "Spectate")
                .withDisabled(!accepting)
        )
    }

    fun usernameModal(action: PanelAction, eventId: Int): Modal {
        val input = TextInput.create(this.USERNAME_FIELD, TextInputStyle.SHORT)
            .setPlaceholder("senseiwells")
            .setRequiredRange(3, 16)
            .build()

        return Modal.create(this.modalId(action, eventId), "Link your Minecraft account")
            .addComponents(Label.of("Minecraft username", "We only ask for this once.", input))
            .build()
    }
}
