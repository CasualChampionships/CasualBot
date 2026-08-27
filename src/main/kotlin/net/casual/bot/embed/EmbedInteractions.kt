package net.casual.bot.embed

import dev.minn.jda.ktx.coroutines.await
import net.casual.bot.config.EmbedBlockData
import net.casual.bot.utils.EventEmbeds
import net.casual.bot.utils.parseHexColour
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent

object EmbedInteractions {
    suspend fun onModal(interaction: ModalInteractionEvent): Boolean {
        val (groupName, index) = EmbedComponents.parseModal(interaction.modalId) ?: return false

        val hook = interaction.deferReply(true).await()
        val group = EmbedStore.group(groupName)
        if (group == null) {
            hook.editOriginalEmbeds(
                EventEmbeds.failure("That group is gone", "It was deleted while the editor was open.")
            ).await()
            return true
        }

        val description = interaction.getValue(EmbedComponents.DESCRIPTION_FIELD)?.asString.orEmpty()
        if (description.isBlank()) {
            hook.editOriginalEmbeds(
                EventEmbeds.failure("The text was empty", "A block needs something to say.")
            ).await()
            return true
        }

        val rawColour = interaction.getValue(EmbedComponents.COLOUR_FIELD)?.asString
        val colour = parseHexColour(rawColour)
        if (!rawColour.isNullOrBlank() && colour == null) {
            hook.editOriginalEmbeds(EventEmbeds.badColour(EmbedComponents.COLOUR_EXAMPLE)).await()
            return true
        }

        if (index != null && index !in group.blocks.indices) {
            hook.editOriginalEmbeds(
                EventEmbeds.failure("That block is gone", "It was removed while the editor was open.")
            ).await()
            return true
        }

        val existing = index?.let { group.blocks[it] }
        val block = EmbedBlockData(
            interaction.getValue(EmbedComponents.TITLE_FIELD)?.asString.orEmpty().take(256),
            description,
            colour ?: existing?.color ?: 0xAA9BFF
        )

        if (index == null) {
            EmbedStore.addBlock(group.name, block)
        } else {
            EmbedStore.updateBlock(group.name, index, block)
        }

        val total = EmbedStore.group(group.name)?.blocks?.size ?: 0
        hook.editOriginalEmbeds(
            EventEmbeds.notice(
                if (index == null) "Block added" else "Block updated",
                "**${EventEmbeds.escape(group.name)}** now has $total block(s). " +
                    "Check it with `/embed preview group:${group.name}`, then `/embed publish` when you're happy"
            )
        ).await()
        return true
    }
}
