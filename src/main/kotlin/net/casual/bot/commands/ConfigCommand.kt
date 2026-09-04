package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import net.casual.bot.BotVersion
import net.casual.bot.CasualBot
import net.casual.bot.utils.CommandUtils.isOrganizer
import net.casual.bot.event.EventService
import net.casual.bot.utils.EventEmbeds
import dev.minn.jda.ktx.messages.Embed
import net.casual.bot.config.BotConfig
import net.casual.bot.config.BotConfigData
import net.casual.bot.embed.EmbedStore
import net.casual.bot.utils.impl.LoadingMessage
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

object ConfigCommand: Command {
    override val name = "config"
    override val description = "Update the bot's config"

    override fun build(command: SlashCommandData) {
        command.subcommand("show", "Display the current config")
        command.subcommand("export", "Exports the current config as a json")
        command.subcommand("import", "Upload an updated config for the bot to use") {
            option<Message.Attachment>("file", "The JSON file to upload", true)
            option<Boolean>("apply", "Whether to apply the updated config - defaults to preview")
        }
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        if (!command.isOrganizer()) {
            loading.replace(EventEmbeds.organizersOnly())
            return
        }

        when (command.subcommandName) {
            "show" -> loading.replace(this.showConfig())
            "export" -> this.exportConfig(loading)
            "import" -> this.importConfig(command, loading)
        }
    }

    private suspend fun exportConfig(loading: LoadingMessage) {
        val raw = BotConfig.serialize()
        loading.replace(
            embeds = listOf(
                EventEmbeds.notice("Exported", "Edit this and send it back with `/config import`.")
            ),
            attachments = listOf(FileUpload.fromData(raw.toByteArray(), "casual-bot-config.json"))
        )
    }

    private suspend fun importConfig(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val attachment = command.getOption<Message.Attachment>("file")!!
        val raw = runCatching {
            CasualBot.httpClient.get(attachment.url).bodyAsText()
        }.getOrNull()

        if (raw == null) {
            loading.replace(EventEmbeds.failure("Couldn't read that file", "Try uploading it again."))
            return
        }

        val parsed = BotConfig.parse(raw)
        val snapshot = parsed.getOrNull()
        if (snapshot == null) {
            loading.replace(
                EventEmbeds.failure(
                    "That file isn't valid",
                    "```\n${(parsed.exceptionOrNull()?.message ?: "Unknown error").take(300)}\n```"
                )
            )
            return
        }

        val summary = this.summarize(snapshot)
        val apply = command.getOption<Boolean>("apply") ?: false
        if (apply) {
            BotConfig.replace(snapshot)
        }

        loading.replace(
            EventEmbeds.notice(
                if (apply) "Imported" else "Preview: import",
                buildString {
                    append("**${summary.channels}** channel(s), ")
                    append("**${summary.groups}** group(s), ")
                    append("**${summary.blocks}** block(s).\n")
                    if (summary.replaced.isNotEmpty()) {
                        append("\nReplaced: ${summary.replaced.joinToString(", ") { EventEmbeds.escape(it) }}")
                    }
                    if (summary.added.isNotEmpty()) {
                        append("\nAdded: ${summary.added.joinToString(", ") { EventEmbeds.escape(it) }}")
                    }
                    if (!apply) {
                        append("\n\nNothing has been written. Run it again with `apply:True` to commit.")
                    }
                }.take(MessageEmbed.DESCRIPTION_MAX_LENGTH)
            )
        )
    }

    private fun summarize(incoming: BotConfigData): ImportSummary {
        val existing = EmbedStore.groups().map { it.name.lowercase() }.toSet()
        val names = incoming.groups.map { it.name }
        return ImportSummary(
            incoming.channels().count { it.second != null },
            names.size,
            incoming.groups.sumOf { it.blocks.size },
            names.filter { it.lowercase() in existing },
            names.filter { it.lowercase() !in existing }
        )
    }

    private fun showConfig(): MessageEmbed {
        return Embed {
            title = "Config"
            color = EventEmbeds.NEUTRAL
            description = buildString {
                append("**Channels**\n")
                for ((label, id) in BotConfig.read { channels() }) {
                    append("- $label: ")
                    append(if (id == null) "*not set*" else "<#$id>")
                    append("\n")
                }

                append("\n**Teams**\n")
                val spectators = EventService.spectatorTeam()
                append("- Spectator team: `${BotConfig.read { spectatorTeam }}`")
                append(if (spectators == null) " *(no team with that name)*\n" else "\n")
                append("- Playing teams: **${EventService.playingTeams().size}**\n")

                append("\n**Database**\n")
                append("- Schema: `${CasualBot.getDatabaseName()}`\n")
                append("- Dev: **${CasualBot.state.dev}**\n")
                append("- Twisted: **${CasualBot.state.twisted}**\n")

                append("\n**Bot**\n")
                append("- Version: `${BotVersion.version}`\n")
            }.take(MessageEmbed.DESCRIPTION_MAX_LENGTH)
        }
    }

    private data class ImportSummary(
        val channels: Int,
        val groups: Int,
        val blocks: Int,
        val replaced: List<String>,
        val added: List<String>
    )
}
