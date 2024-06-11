package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.casual.bot.CasualBot
import net.casual.bot.commands.stats.MinigameStatExpressions
import net.casual.bot.util.CommandUtils
import net.casual.bot.util.EmbedUtil
import net.casual.bot.util.ImageUtil
import net.casual.bot.util.StringUtil.capitalise
import net.casual.bot.util.StringUtil.capitaliseAll
import net.casual.bot.util.impl.LoadingMessage
import net.casual.database.EventPlayers
import net.casual.database.Events
import net.casual.database.MinigamePlayers
import net.casual.database.Minigames
import net.casual.database.stats.DuelMinigameStats
import net.casual.database.stats.MinigameStats
import net.casual.database.stats.UHCMinigameStats
import net.casual.stat.FormattedStat
import net.casual.util.Named
import net.casual.util.sum
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.FileUpload
import org.jetbrains.exposed.sql.*
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

object StatCommand: Command {
    override val name = "stat"
    override val description = "Shows a specified player's stats"

    private val minigames = HashMap<String, MinigameStatExpressions>()

    init {
        provider("duels", DuelMinigameStats) {
            stat("kills", DuelMinigameStats.kills.sum())
            lifetimeStat("wins", DuelMinigameStats.won.sum())
        }
        provider("uhc", UHCMinigameStats) {
            stat("kills", UHCMinigameStats.kills.sum())
            stat("damage_dealt", UHCMinigameStats.damageDealt.sum())
            stat("damage_taken", UHCMinigameStats.damageTaken.sum())
            stat("damage_healed", UHCMinigameStats.damageHealed.sum())
            lifetimeStat("wins", UHCMinigameStats.won.sum())
            lifetimeStat("deaths", UHCMinigameStats.died.sum())
        }
    }

    private fun provider(name: String, stats: MinigameStats, body: MinigameStatExpressions.Builder.() -> Unit) {
        val builder = MinigameStatExpressions.Builder()
        builder.body()
        val provider = builder.build(stats)
        minigames[name] = provider
    }

    override fun build(command: SlashCommandData) {
        command.restrict(true)

        val events = CasualBot.database.getEvents()
        for (minigame in minigames.keys) {
            command.subcommand(minigame, "The minigame of the stat you want to display") {
                CommandUtils.addPlayerArgument(this)
                option<String>("event", "The event you want to display the scoreboard for") {
                    for (event in events) {
                        choice(event.name, event.name)
                    }
                }
            }
        }
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val minigame = command.subcommandName!!

        val (profile, username) = CommandUtils.getMojangProfile(command)
        if (profile == null) {
            command.replyEmbeds(EmbedUtil.somethingWentWrongEmbed("$username is not a valid username!")).queue()
            return
        }

        val expressions = minigames[minigame]
        if (expressions == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Unable to fetch stats")).queue()
            return
        }


        val event = command.getOption<String>("event")
        val stats = CasualBot.database.transaction {
            getStats(profile.id, event, expressions)
        }

        if (stats.isNullOrEmpty()) {
            loading.replace(EmbedUtil.noStatsEmbed(profile.name)).queue()
            return
        }

        val image = ImageUtil.playerStatsImage(profile.name, minigame.capitalise(), stats)
        ByteArrayOutputStream().use { stream ->
            ImageIO.write(image, "png", stream)
            val file = FileUpload.fromData(stream.toByteArray(), "stats.png")
            loading.replace(attachments = listOf(file)).queue()
        }
    }

    private fun getStats(uuid: UUID, event: String?, expressions: MinigameStatExpressions): List<Named<FormattedStat>>? {
        if (event == null) {
            val statExpressions = ArrayList<Named<Expression<*>>>()
            for ((name, expression) in expressions.entries()) {
                if (expression.lifetime != null) {
                    statExpressions.add(Named(name.capitaliseAll("_"), expression.lifetime))
                }
            }

            val row = expressions.stats.joinedWithEventPlayers()
                .select(statExpressions.map { it.value })
                .where { EventPlayers.uuid eq uuid }
                .groupBy(EventPlayers.uuid)
                .firstOrNull() ?: return null

            return statExpressions.map { (name, expression) ->
                Named(name, FormattedStat.of(row[expression]!!))
            }
        }

        val minigames = Minigames.join(Events, JoinType.INNER, additionalConstraint = { Minigames.event eq Events.id })
            .selectAll()
            .where { Events.name eq event }
            .map { it[Minigames.id] }

        val statExpressions = ArrayList<Named<Expression<*>>>()
        for ((name, expression) in expressions.entries()) {
            if (expression.minigame != null) {
                statExpressions.add(Named(name.capitaliseAll("_"), expression.minigame))
            }
        }

        val row = expressions.stats.joinedWithEventPlayers()
            .select(statExpressions.map { it.value })
            .where { (EventPlayers.uuid eq uuid) and (MinigamePlayers.minigame inList minigames)  }
            .groupBy(EventPlayers.uuid)
            .firstOrNull() ?: return null

        return statExpressions.map { (name, expression) ->
            Named(name, FormattedStat.of(row[expression]!!))
        }
    }
}