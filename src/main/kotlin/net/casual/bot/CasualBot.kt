package net.casual.bot

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.jdabuilder.intents
import dev.minn.jda.ktx.jdabuilder.light
import io.github.oshai.kotlinlogging.KotlinLogging
import net.casual.bot.commands.ReloadCommand
import net.casual.bot.commands.ScoreboardCommand
import net.casual.bot.commands.StatCommand
import net.casual.bot.commands.TeamCommand
import net.casual.bot.config.Config
import net.casual.bot.util.DatabaseUtils.resolveScoreboard
import net.casual.bot.util.EmbedUtil
import net.casual.bot.util.ImageUtil
import net.casual.bot.util.MessageUtil
import net.casual.bot.util.MessageUtil.loading
import net.casual.database.CasualDatabase
import net.casual.database.stats.UHCMinigameStats
import net.casual.util.sum
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.FileUpload
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import javax.imageio.ImageIO

object CasualBot: CoroutineEventListener {
    val logger = KotlinLogging.logger("CasualBot")
    var config = Config.read()
        private set

    val database = createDatabase()

    val jda = light(config.token, enableCoroutines = true) {
        intents += GatewayIntent.MESSAGE_CONTENT
        addEventListeners(this@CasualBot)
    }

    val guild by lazy { jda.getGuildById(config.guildId) }

    private val commands = listOf(ReloadCommand, ScoreboardCommand, StatCommand, TeamCommand).associateBy { it.name }

    @JvmStatic
    fun main(args: Array<String>) {

    }

    fun reloadConfig() {
        this.config = Config.read()
    }

    suspend fun reloadCommands() {
        val guild = guild ?: return
        for (command in guild.retrieveCommands().await()) {
            command.delete().queue()
        }

        jda.updateCommands {
            for (command in commands.values) {
                slash(command.name, command.description) {
                    command.build(this)
                }
            }
        }.queue()
    }

    suspend fun reloadEmbeds() {
        val info = config.embedOrDefault("info")
        val faq = config.embedOrDefault("faq")
        val rules = config.embedOrDefault("rules")

        val channels = config.channelIds

        val channel = jda.getTextChannelById(channels.wins)
        if (channel != null) {
            // FIXME:
            val wins = database.resolveScoreboard(database.transaction {
                UHCMinigameStats.lifetimeScoreboard(UHCMinigameStats.won.sum())
            })
            if (wins.isNotEmpty()) {
                val image = ImageUtil.scoreboardImage("UHC Wins", wins)
                ByteArrayOutputStream().use {
                    ImageIO.write(image, "png", it)
                    channel.editMessageAttachmentsById(
                        channel.latestMessageIdLong,
                        FileUpload.fromData(it.toByteArray(), "scoreboard.png")
                    )
                }
            }
        }

        MessageUtil.replaceFirstMessages(jda, channels.rules, listOf(""), listOf(rules))
        MessageUtil.replaceFirstMessages(jda, channels.info, listOf(""), listOf(info, faq))
    }

    override suspend fun onEvent(event: GenericEvent) {
        when (event) {
            is ReadyEvent -> onReady()
            is MessageReceivedEvent -> onMessageReceived(event)
            is SlashCommandInteractionEvent -> onSlashCommandInteraction(event)
            is ShutdownEvent -> onShutdown()
        }
    }

    private suspend fun onReady() {
        logger.info { "CasualBot has started!" }

        reloadCommands()
        reloadEmbeds()
    }

    private fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.channel.idLong == config.channelIds.suggestions && event.author != jda.selfUser) {
            val message = event.message
            message.createThreadChannel(message.contentRaw).queue()
            message.addReaction(Emoji.fromUnicode("\uD83D\uDC4D")).queue()
            message.addReaction(Emoji.fromUnicode("\uD83D\uDC4E")).queue()
            event.channel.iterableHistory.find { it.author == jda.selfUser }?.delete()?.queue()
            event.channel.sendMessageEmbeds(config.embedOrDefault("suggestions")).queue()
        }
    }

    private suspend fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val command = commands[event.name] ?: return
        val loading = event.loading()
        try {
            command.execute(event, loading)
        } catch (e: Exception) {
            val message =  when (e) {
                is SocketTimeoutException -> "Error occured while running command, is the database down? Check logs..."
                else -> "Error occurred while running command, check logs for more info..."
            }
            loading.replace(EmbedUtil.somethingWentWrongEmbed(message)).queue()
            logger.error(e) { "An error occurred while running the command ${event.name}" }
        } finally {
            if (!loading.hasReplaced) {
                loading.replace(EmbedUtil.somethingWentWrongEmbed("Message didn't get updated properly!!!")).queue()
            }
        }
    }

    private fun onShutdown() {
        database.close()
    }

    private fun createDatabase(): CasualDatabase {
        val login = config.databaseLogin
        val database = CasualDatabase(login.url, login.username, login.password, DatabaseConfig {
            sqlLogger = object: SqlLogger {
                override fun log(context: StatementContext, transaction: Transaction) {
                    logger.info { context.expandArgs(transaction) }
                }
            }
        })
        database.initialize()
        return database
    }
}