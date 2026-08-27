package net.casual.bot.embed

import net.casual.bot.config.EmbedBlockData
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.modals.Modal

object EmbedComponents {
    const val COLOUR_EXAMPLE = "#AA9BFF"

    private const val PREFIX = "embed"
    const val TITLE_FIELD = "title"
    const val DESCRIPTION_FIELD = "description"
    const val COLOUR_FIELD = "colour"

    fun modalId(groupName: String, index: Int?): String {
        return "$PREFIX:block:$groupName:${index ?: "new"}"
    }

    fun parseModal(id: String): Pair<String, Int?>? {
        val parts = id.split(":")
        if (parts.size != 4 || parts[0] != this.PREFIX || parts[1] != "block") {
            return null
        }
        if (parts[3] == "new") {
            return parts[2] to null
        }
        val index = parts[3].toIntOrNull() ?: return null
        return parts[2] to index
    }

    fun blockModal(groupName: String, index: Int?, block: EmbedBlockData?): Modal {
        val title = TextInput.create(this.TITLE_FIELD, TextInputStyle.SHORT)
            .setRequired(false)
            .setMaxLength(256)
            .setValue(block?.title?.ifEmpty { null })
            .build()

        val description = TextInput.create(this.DESCRIPTION_FIELD, TextInputStyle.PARAGRAPH)
            .setRequired(true)
            .setMaxLength(4000)
            .setValue(block?.description)
            .build()

        val colour = TextInput.create(this.COLOUR_FIELD, TextInputStyle.SHORT)
            .setRequired(false)
            .setMaxLength(9)
            .setPlaceholder(COLOUR_EXAMPLE)
            .setValue(block?.let { "#%06X".format(it.color) })
            .build()

        val heading = if (block == null) "New block in $groupName" else "Edit block in $groupName"
        return Modal.create(this.modalId(groupName, index), heading.take(45))
            .addComponents(
                Label.of("Heading", "Leave empty for no heading.", title),
                Label.of("Text", description),
                Label.of("Colour", "Hex, for example $COLOUR_EXAMPLE.", colour)
            )
            .build()
    }
}
