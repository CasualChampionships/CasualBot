package net.casual.bot.commands

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.Embed
import net.casual.bot.CasualBot
import net.casual.bot.config.EmbedGroupData
import net.casual.bot.embed.EmbedComponents
import net.casual.bot.embed.EmbedStore
import net.casual.bot.utils.CommandUtils
import net.casual.bot.utils.CommandUtils.isOrganizer
import net.casual.bot.utils.EventEmbeds
import net.casual.bot.utils.MessageUtil
import net.casual.bot.utils.impl.LoadingMessage
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

object EmbedCommand: Command {
    override val name = "embed"
    override val description = "Review and modify the bot embeds"

    override fun build(command: SlashCommandData) {
        command.subcommand("list", "Show every group and where it publishes")
        command.subcommand("create", "Make a new group of messages") {
            option<String>("name", "A short name, for example rules", true)
            option<GuildMessageChannel>("channel", "Where this group publishes")
            option<String>("title", "Text shown above the embeds")
        }
        command.subcommand("delete", "Delete a group") {
            groupOption(this)
        }
        command.subcommand("add", "Add a block to a group") {
            groupOption(this)
        }
        command.subcommand("edit", "Change a block, or the group's title") {
            groupOption(this)
            option<Int>("block", "Which block, starting at 1. Leave empty to edit the group title")
            option<String>("title", "Only when editing the group title")
        }
        command.subcommand("move", "Reorder a block") {
            groupOption(this)
            option<Int>("block", "Which block, starting at 1", true)
            option<Int>("to", "Its new position, starting at 1", true)
        }
        command.subcommand("remove", "Delete a block") {
            groupOption(this)
            option<Int>("block", "Which block, starting at 1", true)
        }
        command.subcommand("image", "Attach images to a group") {
            groupOption(this)
            option<String>("url", "Image URL to add. Leave empty to remove them all")
        }
        command.subcommand("channel", "Change where a group publishes") {
            groupOption(this)
            option<GuildMessageChannel>("channel", "The channel. Leave empty to unset it")
        }
        command.subcommand("preview", "See a group without publishing it") {
            groupOption(this)
        }
        command.subcommand("publish", "Publish a group's channel") {
            groupOption(this)
        }
    }

    private fun groupOption(data: SubcommandData) {
        data.option<String>("group", "The group name", true, autocomplete = true)
    }

    override suspend fun autocomplete(event: CommandAutoCompleteInteractionEvent) {
        if (event.focusedOption.name != "group") {
            return
        }
        val query = event.focusedOption.value.lowercase()
        val choices = EmbedStore.groups()
            .map { it.name }
            .filter { it.lowercase().startsWith(query) }
            .take(CommandUtils.MAX_CHOICES)
            .map { Choice(it, it) }
        event.replyChoices(choices).await()
    }

    override suspend fun modal(command: SlashCommandInteractionEvent): Boolean {
        if (!command.isOrganizer()) {
            return false
        }
        val sub = command.subcommandName
        if (sub != "add" && sub != "edit") {
            return false
        }

        val index = command.getOption<Int>("block")
        if (sub == "edit" && index == null) {
            return false
        }

        val group = EmbedStore.group(command.getOption<String>("group")!!) ?: return false
        val block = if (index == null) null else group.blocks.getOrNull(index - 1)
        if (sub == "edit" && block == null) {
            return false
        }

        command.replyModal(EmbedComponents.blockModal(group.name, index?.minus(1), block)).await()
        return true
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        if (!command.isOrganizer()) {
            loading.replace(EventEmbeds.organizersOnly())
            return
        }

        when (command.subcommandName) {
            "list" -> loading.replace(this.listGroups())
            "create" -> this.createGroup(command, loading)
            "delete" -> this.deleteGroup(command, loading)
            "add" -> this.withGroup(command, loading) { }
            "edit" -> this.editGroup(command, loading)
            "move" -> this.moveBlock(command, loading)
            "remove" -> this.removeBlock(command, loading)
            "image" -> this.image(command, loading)
            "channel" -> this.setChannel(command, loading)
            "preview" -> this.preview(command, loading)
            "publish" -> this.publish(command, loading)
        }
    }

    private fun listGroups(): MessageEmbed {
        val groups = EmbedStore.groups()
        if (groups.isEmpty()) {
            return EventEmbeds.notice("No groups yet", "Make one with `/embed create`")
        }
        return Embed {
            title = "Message groups"
            color = EventEmbeds.NEUTRAL
            description = groups.joinToString("\n\n") { group ->
                buildString {
                    append("**${EventEmbeds.escape(group.name)}**\n")
                    append("Channel: ")
                    append(group.channelId?.let { "<#$it>" } ?: "*not set*")
                    append("\n${group.blocks.size} block(s)")
                    if (group.images.isNotEmpty()) {
                        append(" - ${group.images.size} image(s)")
                    }
                }
            }.take(MessageEmbed.DESCRIPTION_MAX_LENGTH)
        }
    }

    private suspend fun createGroup(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val name = command.getOption<String>("name")!!.trim()
        if (!EmbedStore.nameFormat.matches(name)) {
            loading.replace(
                EventEmbeds.failure(
                    "That name won't work",
                    "Use letters, numbers, dashes and underscores only, up to 64 characters"
                )
            )
            return
        }
        if (EmbedStore.exists(name)) {
            loading.replace(
                EventEmbeds.failure("There's already a group called $name", "Pick a different name")
            )
            return
        }

        val channel = command.getOption<GuildMessageChannel>("channel")
        EmbedStore.createGroup(name, command.getOption<String>("title") ?: "", channel?.idLong)

        loading.replace(
            EventEmbeds.notice(
                "Created ${EventEmbeds.escape(name)}",
                "Add some text with `/embed add group:$name`"
            )
        )
    }

    private suspend fun deleteGroup(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val group = this.withGroup(command, loading) { it } ?: return
        EmbedStore.deleteGroup(group.name)
        loading.replace(
            EventEmbeds.notice(
                "Deleted ${EventEmbeds.escape(group.name)}",
                "It is no longer published anywhere"
            )
        )
    }

    private suspend fun editGroup(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val group = this.withGroup(command, loading) { it } ?: return
        if (command.getOption<Int>("block") != null) {
            loading.replace(this.noSuchBlock(group))
            return
        }

        val title = command.getOption<String>("title")
        if (title == null) {
            loading.replace(
                EventEmbeds.failure(
                    "Nothing to edit",
                    "Give `block:` to change a block, or `title:` to change the group's title"
                )
            )
            return
        }

        EmbedStore.setTitle(group.name, title)
        loading.replace(
            EventEmbeds.notice("Updated ${EventEmbeds.escape(group.name)}", "Group title changed")
        )
    }

    private suspend fun moveBlock(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val group = this.withGroup(command, loading) { it } ?: return
        val from = command.getOption<Int>("block")!! - 1
        if (from !in group.blocks.indices) {
            loading.replace(this.noSuchBlock(group))
            return
        }

        EmbedStore.moveBlock(group.name, from, command.getOption<Int>("to")!! - 1)
        loading.replace(
            EventEmbeds.notice("Reordered ${EventEmbeds.escape(group.name)}", this.blockSummary(group.name))
        )
    }

    private suspend fun removeBlock(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val group = this.withGroup(command, loading) { it } ?: return
        val index = command.getOption<Int>("block")!! - 1
        if (index !in group.blocks.indices) {
            loading.replace(this.noSuchBlock(group))
            return
        }

        EmbedStore.removeBlock(group.name, index)
        loading.replace(EventEmbeds.notice("Removed a block", this.blockSummary(group.name)))
    }

    private suspend fun image(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val group = this.withGroup(command, loading) { it } ?: return
        val url = command.getOption<String>("url")

        if (url == null) {
            EmbedStore.clearImages(group.name)
            loading.replace(
                EventEmbeds.notice("Images removed", "${EventEmbeds.escape(group.name)} has no images now")
            )
            return
        }

        val downloaded = EmbedStore.download(url)
        if (downloaded == null) {
            loading.replace(
                EventEmbeds.failure(
                    "That image couldn't be downloaded",
                    "Check the link opens directly to an image file. Nothing was added"
                )
            )
            return
        }

        EmbedStore.addImage(group.name, url)
        loading.replace(
            embeds = listOf(
                EventEmbeds.notice(
                    "Image added",
                    "Uploading as `${downloaded.name}`. " +
                        "${group.images.size + 1} image(s) on ${EventEmbeds.escape(group.name)}"
                )
            ),
            attachments = listOf(downloaded)
        )
    }

    private suspend fun setChannel(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val group = this.withGroup(command, loading) { it } ?: return
        val channel = command.getOption<GuildMessageChannel>("channel")
        EmbedStore.setChannel(group.name, channel?.idLong)

        loading.replace(
            EventEmbeds.notice(
                "Updated ${EventEmbeds.escape(group.name)}",
                if (channel == null) {
                    "It publishes nowhere now"
                } else {
                    "It publishes to <#${channel.idLong}>"
                }
            )
        )
    }

    private suspend fun preview(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val group = this.withGroup(command, loading) { it } ?: return
        if (EmbedStore.isEmpty(group)) {
            loading.replace(
                EventEmbeds.notice(
                    "${EventEmbeds.escape(group.name)} is empty",
                    "Add something with `/embed add`"
                )
            )
            return
        }

        val data = EmbedStore.toMessageData(group)
        loading.replace(
            content = data.content.ifEmpty { null },
            embeds = data.embeds,
            attachments = data.attachments
        )
    }

    private suspend fun publish(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val group = this.withGroup(command, loading) { it } ?: return
        val channelId = group.channelId
        if (channelId == null) {
            loading.replace(
                EventEmbeds.failure(
                    "${EventEmbeds.escape(group.name)} has no channel",
                    "Set one with `/embed channel group:${group.name}`"
                )
            )
            return
        }

        val siblings = EmbedStore.groupsForChannel(channelId)
        MessageUtil.editLastMessages(CasualBot.jda, channelId, siblings.map { EmbedStore.toMessageData(it) })

        loading.replace(
            EventEmbeds.notice(
                "Published to <#$channelId>",
                "${siblings.size} group(s): ${siblings.joinToString(", ") { EventEmbeds.escape(it.name) }}"
            )
        )
    }

    private suspend fun <T> withGroup(
        command: GenericCommandInteractionEvent,
        loading: LoadingMessage,
        block: (EmbedGroupData) -> T
    ): T? {
        val name = command.getOption<String>("group")!!
        val group = EmbedStore.group(name)
        if (group == null) {
            loading.replace(
                EventEmbeds.failure(
                    "There's no group called ${EventEmbeds.escape(name)}",
                    "Pick one from the suggestions as you type"
                )
            )
            return null
        }
        return block(group)
    }

    private fun blockSummary(name: String): String {
        val blocks = EmbedStore.group(name)?.blocks ?: listOf()
        if (blocks.isEmpty()) {
            return "No blocks left."
        }
        return blocks.mapIndexed { index, block ->
            "${index + 1}. ${EventEmbeds.escape(block.title.ifEmpty { block.description.take(40) })}"
        }.joinToString("\n")
    }

    private fun noSuchBlock(group: EmbedGroupData): MessageEmbed {
        val count = group.blocks.size
        return EventEmbeds.failure(
            "That block doesn't exist",
            if (count == 0) {
                "${EventEmbeds.escape(group.name)} has no blocks yet."
            } else {
                "${EventEmbeds.escape(group.name)} has $count block(s), numbered 1 to $count."
            }
        )
    }
}
