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
import net.casual.bot.commands.*
import net.casual.bot.config.BotConfig
import net.casual.bot.config.BotState
import net.casual.bot.config.Env
import net.casual.bot.database.BotDatabase.initializeBotTables
import net.casual.bot.panel.PanelInteractions
import net.casual.bot.panel.RegistrationPanel
import net.casual.bot.event.EventService
import net.casual.bot.utils.EventEmbeds
import net.casual.bot.utils.ImageUtil
import net.casual.bot.utils.ImageUtil.toFileUpload
import net.casual.bot.utils.MessageUtil
import net.casual.bot.utils.MessageUtil.loading
import net.casual.bot.utils.Named
import net.casual.database.CasualDatabase
import net.casual.database.DiscordTeam
import net.casual.database.DiscordTeams
import net.casual.stat.FormattedStat
import net.dv8tion.jda.api.entities.ScheduledEvent
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.ScheduledEventCreateEvent
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateStatusEvent
import net.casual.bot.commands.ConfirmInteractions
import net.casual.bot.embed.EmbedInteractions
import net.casual.bot.embed.EmbedStore
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.expandArgs
import java.net.SocketTimeoutException
import java.time.ZoneId


object CasualBot : CoroutineEventListener {
    private const val SUGGESTIONS_HISTORY = 50

    val logger = KotlinLogging.logger("CasualBot")
    val httpClient = HttpClient(CIO)

    var env = Env.read()
        private set

    var state = BotState.read()
        private set

    init {
        BotConfig.load()
    }

    var database = this.createDatabase()
        private set

    val jda = light(this.env.botToken, enableCoroutines = true) {
        enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.SCHEDULED_EVENTS)
        enableCache(CacheFlag.SCHEDULED_EVENTS)
        addEventListeners(this@CasualBot)
    }

    val guild by lazy { this.jda.getGuildById(this.env.guildId) }

    private val commands = listOf(
        AdminCommand,
        ConfigCommand,
        EmbedCommand,
        EventCommand,
        MeCommand,
        ReloadCommand,
        ScoreboardCommand,
        StatCommand,
        TeamCommand
    ).associateBy(Command::name)

    @JvmStatic
    fun main(args: Array<String>) {
        
    }

    fun modifyState(dev: Boolean = this.state.dev, twisted: Boolean = this.state.twisted): Boolean {
        this.state = this.state.copy(dev = dev, twisted = twisted)
        val written = BotState.write(this.state)
        this.reloadDatabase()
        return written
    }

    fun getDatabaseName(): String {
        val base = if (this.state.twisted) this.env.databaseTwistedName else this.env.databaseCanonName
        return if (this.state.dev) "${base}_debug" else base
    }

    fun reloadConfig() {
        BotConfig.load()
    }

    fun reloadDatabase() {
        val previous = this.database
        this.database = this.createDatabase()
        previous.close()
    }

    suspend fun reloadCommands() {
        val guild = guild ?: return
        for (command in guild.retrieveCommands().await()) {
            command.delete().queue()
        }

        this.jda.updateCommands {
            for (command in commands.values) {
                slash(command.name, command.description) {
                    command.build(this)
                }
            }
        }.queue()
    }

    suspend fun reloadEmbeds() {
        for (channelId in EmbedStore.channels()) {
            val messages = EmbedStore.groupsForChannel(channelId).map { EmbedStore.toMessageData(it) }
            MessageUtil.editLastMessages(this.jda, channelId, messages)
        }

        if (!this.state.twisted) {
            BotConfig.read { winsChannel }?.let {
                MessageUtil.editLastMessages(this.jda, it, this.createTeamWinsMessage())
            }
        }
    }

    override suspend fun onEvent(event: GenericEvent) {
        when (event) {
            is ReadyEvent -> this.onReady()
            is MessageReceivedEvent -> this.onMessageReceived(event)
            is SlashCommandInteractionEvent -> this.onSlashCommandInteraction(event)
            is ButtonInteractionEvent -> this.onButtonInteraction(event)
            is CommandAutoCompleteInteractionEvent -> this.onAutoComplete(event)
            is ModalInteractionEvent -> this.onModalInteraction(event)
            is ShutdownEvent -> this.onShutdown()
            is ScheduledEventCreateEvent -> this.onScheduledEventCreate(event)
            is ScheduledEventUpdateStatusEvent -> this.onScheduledEventUpdateStatus(event)
        }
    }

    private suspend fun onReady() {
        this.logger.info { "CasualBot (${BotVersion.version}) has started!" }

        this.reloadCommands()
        this.reloadEmbeds()
        RegistrationPanel.reattach()
    }

    private suspend fun onAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        try {
            this.commands[event.name]?.autocomplete(event)
        } catch (e: Exception) {
            this.logger.error(e) { "An error occurred completing ${event.name}" }
        }
    }

    private suspend fun onButtonInteraction(event: ButtonInteractionEvent) {
        try {
            if (ConfirmInteractions.onButton(event)) {
                return
            }
            PanelInteractions.onButton(event)
        } catch (e: Exception) {
            this.logger.error(e) { "An error occurred handling the button ${event.componentId}" }
        }
    }

    private suspend fun onModalInteraction(event: ModalInteractionEvent) {
        try {
            if (EmbedInteractions.onModal(event)) {
                return
            }
            PanelInteractions.onModal(event)
        } catch (e: Exception) {
            this.logger.error(e) { "An error occurred handling the modal ${event.modalId}" }
        }
    }

    private suspend fun onScheduledEventCreate(event: ScheduledEventCreateEvent) {
        val name = event.scheduledEvent.name
        val desc = event.scheduledEvent.description ?: ""
        val time = event.scheduledEvent.startTime.toLocalDateTime()
        val unix = time.atZone(ZoneId.of("UTC")).toEpochSecond()

        val statusChannelId = BotConfig.read { statusChannel }
        if (statusChannelId != null) {
            val embed = MessageCreateBuilder()
                .setContent("@everyone")
                .setEmbeds(EventEmbeds.nextEvent(name, unix, desc))
                .build()
            MessageUtil.editLastMessages(event.jda, statusChannelId, embed)
        }

        if (this.state.twisted) {
            return
        }

        val message = "You can now begin creating teams for the $name! Remember you **__do not__** need " +
            "a full team in order to play. If you have any difficulties or questions feel free to ping " +
            "Santa or Sensei!"
        for (team in EventService.playingTeams()) {
            val teamChannelId = team.channelId ?: continue
            val teamChannel = event.jda.getTextChannelById(teamChannelId) ?: continue
            teamChannel.sendMessage(message).queue()
        }
    }

    private suspend fun onScheduledEventUpdateStatus(event: ScheduledEventUpdateStatusEvent) {
        val status = event.newStatus

        if (status == ScheduledEvent.Status.COMPLETED) {
            val channelId = BotConfig.read { statusChannel } ?: return
            val embed = MessageCreateBuilder().setEmbeds(EventEmbeds.noEventScheduled()).build()
            MessageUtil.editLastMessages(event.jda, channelId, embed)
        }
    }

    private suspend fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.channel.idLong != BotConfig.read { suggestionsChannel } || event.author == this.jda.selfUser) {
            return
        }

        val message = event.message
        var title = message.contentRaw
        if (title.length > 100) {
            title = title.take(97) + "..."
        }
        message.createThreadChannel(title).queue()
        message.addReaction(Emoji.fromUnicode("\uD83D\uDC4D")).queue()
        message.addReaction(Emoji.fromUnicode("\uD83D\uDC4E")).queue()

        val suggestions = EmbedStore.group("suggestions") ?: return
        val data = EmbedStore.toMessageData(suggestions)
        event.channel.history.retrievePast(SUGGESTIONS_HISTORY).await()
            .firstOrNull { it.author == this.jda.selfUser }
            ?.delete()?.queue()
        event.channel.sendMessage(data).queue()
    }

    private suspend fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val command = this.commands[event.name] ?: return
        if (command.modal(event)) {
            return
        }
        val loading = event.loading(command.ephemeral)
        try {
            command.execute(event, loading)
        } catch (e: Exception) {
            val message = when (e) {
                is SocketTimeoutException -> "The database didn't respond. Check the logs, it may be down."
                else -> "Try the command again. If it keeps failing, ping an admin."
            }
            loading.replaceQuietly(EventEmbeds.wentWrong(message))
            this.logger.error(e) { "An error occurred while running the command ${event.name}" }
        } finally {
            if (!loading.replaced) {
                loading.replaceQuietly(
                    EventEmbeds.failure(
                        "That didn't finish",
                        "The command stopped before it could reply. Try it again, and ping an organizer if it keeps happening."
                    )
                )
            }
        }
    }

    private fun onShutdown() {
        this.database.close()
    }

    private fun createDatabase(): CasualDatabase {
        val url = this.env.databaseUrl + this.getDatabaseName()
        val username = this.env.databaseUsername
        val password = this.env.databasePassword
        val database = CasualDatabase(url, username, password, DatabaseConfig {
            sqlLogger = object : SqlLogger {
                override fun log(context: StatementContext, transaction: Transaction) {
                    logger.info { context.expandArgs(transaction) }
                }
            }
        })
        database.initialize()
        database.initializeBotTables()
        return database
    }

    private fun createTeamWinsMessage(): MessageCreateData {
        val teams = this.database.transaction {
            DiscordTeam.all().orderBy(DiscordTeams.wins to SortOrder.DESC, DiscordTeams.name to SortOrder.ASC)
                .filter { it.channelId != null }
                .map { Named(it.name, FormattedStat.of(it.wins)) }
        }
        val image = ImageUtil.scoreboardImage("UHC Team Wins", teams)
        val file = image.toFileUpload("uhc_team_wins.png")
        return MessageCreate(files = listOf(file))
    }
}