package net.casual.bot.commands

import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button

enum class ConfirmChoice(val id: String) {
    Confirm("yes"),
    Cancel("no");

    companion object {
        fun of(id: String): ConfirmChoice? = entries.firstOrNull { it.id == id }
    }
}

data class Confirmation(
    val choice: ConfirmChoice,
    val action: String,
    val target: String,
    val userId: Long
)

object ConfirmComponents {
    private const val PREFIX = "confirm"

    const val TEAM_DELETE = "team-delete"
    const val EVENT_END = "event-end"

    fun id(choice: ConfirmChoice, action: String, target: String, userId: Long): String {
        return "$PREFIX:${choice.id}:$action:$target:$userId"
    }

    fun parse(id: String): Confirmation? {
        val parts = id.split(":")
        if (parts.size != 5 || parts[0] != this.PREFIX) {
            return null
        }
        val choice = ConfirmChoice.of(parts[1]) ?: return null
        val userId = parts[4].toLongOrNull() ?: return null
        return Confirmation(choice, parts[2], parts[3], userId)
    }

    fun buttons(
        action: String,
        target: String,
        userId: Long,
        confirmLabel: String = "Confirm",
        danger: Boolean = true
    ): ActionRow {
        val confirm = if (danger) {
            Button.danger(this.id(ConfirmChoice.Confirm, action, target, userId), confirmLabel)
        } else {
            Button.success(this.id(ConfirmChoice.Confirm, action, target, userId), confirmLabel)
        }
        val cancel = Button.secondary(this.id(ConfirmChoice.Cancel, action, target, userId), "Cancel")
        return ActionRow.of(confirm, cancel)
    }
}
