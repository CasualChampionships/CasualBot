package net.casual.bot

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.jdabuilder.light
import dev.minn.jda.ktx.messages.MessageCreate
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import net.casual.bot.commands.*
import net.casual.bot.config.Config
import net.casual.bot.util.CollectionUtils.concat
import net.casual.bot.util.EmbedUtil
import net.casual.bot.util.ImageUtil
import net.casual.bot.util.ImageUtil.toFileUpload
import net.casual.bot.util.MessageUtil
import net.casual.bot.util.MessageUtil.loading
import net.casual.bot.util.TwistedUtils
import net.casual.database.CasualDatabase
import net.casual.database.DiscordTeam
import net.casual.database.DiscordTeams
import net.casual.stat.FormattedStat
import net.casual.util.Named
import net.dv8tion.jda.api.entities.ScheduledEvent
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.ScheduledEventCreateEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateStatusEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import java.net.SocketTimeoutException
import java.time.ZoneId


object CasualBot : CoroutineEventListener {
    val logger = KotlinLogging.logger("CasualBot")
    val httpClient = HttpClient(CIO)
    val coroutineScope = CoroutineScope(Dispatchers.Default + Job())

    var config = Config.read()
        private set

    val database = createDatabase()

    val jda = light(config.token, enableCoroutines = true) {
        enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.SCHEDULED_EVENTS)
        enableCache(CacheFlag.SCHEDULED_EVENTS)
        addEventListeners(this@CasualBot)
    }


    val guild by lazy { jda.getGuildById(config.guildId) }

    private val commands = listOf(EventCommand, ReloadCommand, ScoreboardCommand, StatCommand, TeamCommand).associateBy { it.name }

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
        val channels = config.channelIds

        val info = config.embedsByName("info")?.asMessageCreateData()
        val faq = config.embedsByName("faq")?.asMessageCreateData()
        if (info != null && faq != null) {
            MessageUtil.editLastMessages(jda, channels.info, info.concat(faq))
        }

        val rules = config.embedsByName("rules")?.asMessageCreateData()
        if (rules != null) {
            MessageUtil.editLastMessages(jda, channels.rules, rules)
        }

        if (TwistedUtils.isTwistedDatabase(config.databaseLogin.name)) {
            return
        }
        MessageUtil.editLastMessages(jda, channels.wins, createTeamWinsMessage())
    }

    override suspend fun onEvent(event: GenericEvent) {
        when (event) {
            is ReadyEvent -> onReady()
            is MessageReceivedEvent -> onMessageReceived(event)
            is SlashCommandInteractionEvent -> onSlashCommandInteraction(event)
            is ShutdownEvent -> onShutdown()
            is ScheduledEventCreateEvent -> onScheduledEventCreate(event)
            is ScheduledEventUpdateStatusEvent -> onScheduledEventUpdateStatus(event)
        }
    }

    private suspend fun onReady() {
        logger.info { "CasualBot has started!" }

        reloadCommands()
        reloadEmbeds()
    }

    private suspend fun onScheduledEventCreate(event: ScheduledEventCreateEvent) {
        val name = event.scheduledEvent.name
        val desc = event.scheduledEvent.description ?: ""
        val time = event.scheduledEvent.startTime.toLocalDateTime()
        val unix = time.atZone(ZoneId.of("UTC")).toEpochSecond()

        val statusChannelId = config.channelIds.status
        val embed = MessageCreateBuilder().setContent("@everyone").setEmbeds(EmbedUtil.nextEventEmbed(name, unix, desc)).build()

        MessageUtil.editLastMessages(event.jda, statusChannelId, embed)

        if (TwistedUtils.isTwistedDatabase(config.databaseLogin.name)) {
            return
        }

        for (team in database.getDiscordTeams()) {
            val teamChannelId = team.channelId ?: continue
            val message =
                "You can now begin creating teams for the ${event.scheduledEvent.name}! Remember you **__do not__** need a full team in order to play. If you have any difficulties or questions feel free to ping Santa or Sensei! "
            val teamChannel = event.jda.getTextChannelById(teamChannelId) ?: continue
            teamChannel.sendMessage(message).queue()
        }
    }

    private suspend fun onScheduledEventUpdateStatus(event: ScheduledEventUpdateStatusEvent) {
        val status = event.newStatus

        if (status == ScheduledEvent.Status.COMPLETED) {
            val channelId = config.channelIds.status
            val embed = MessageCreateBuilder().setEmbeds(EmbedUtil.noEventScheduledEmbed()).build()
            MessageUtil.editLastMessages(event.jda, channelId, embed)
        }
    }

    private fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.channel.idLong == config.channelIds.suggestions && event.author != jda.selfUser) {
            val message = event.message
            var title = message.contentRaw
            if (title.length > 100) {
                title = title.take(97) + "..."
            }
            message.createThreadChannel(title).queue()
            message.addReaction(Emoji.fromUnicode("\uD83D\uDC4D")).queue()
            message.addReaction(Emoji.fromUnicode("\uD83D\uDC4E")).queue()
            event.channel.iterableHistory.find { it.author == jda.selfUser }?.delete()?.queue()
            val suggestions = config.embedsByName("suggestions")?.asMessageCreateData()
            if (suggestions != null) {
                for (data in suggestions) {
                    event.channel.sendMessage(data).queue()
                }
            }
        }
    }

    private suspend fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val command = commands[event.name] ?: return
        val loading = event.loading()
        try {
            command.execute(event, loading)
        } catch (e: Exception) {
            val message = when (e) {
                is SocketTimeoutException -> "Error occurred while running command, is the database down? Check logs..."
                else -> "Error occurred while running command, try the command again, otherwise please ping an admin"
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
        val database = CasualDatabase(login.url + login.name, login.username, login.password, DatabaseConfig {
            sqlLogger = object : SqlLogger {
                override fun log(context: StatementContext, transaction: Transaction) {
                    logger.info { context.expandArgs(transaction) }
                }
            }
        })
        database.initialize()
        return database
    }

    private fun createTeamWinsMessage(): MessageCreateData {
        val teams = database.transaction {
            DiscordTeam.all().orderBy(DiscordTeams.wins to SortOrder.DESC, DiscordTeams.name to SortOrder.ASC)
                .filter { it.channelId != null }
                .map { Named(it.name, FormattedStat.of(it.wins)) }
        }
        val image = ImageUtil.scoreboardImage("UHC Team Wins", teams)
        val file = image.toFileUpload("uhc_team_wins.png")
        return MessageCreate(files = listOf(file))
    }
}