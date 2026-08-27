package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.casual.bot.CasualBot
import net.casual.bot.commands.stats.MinigameStatExpressions
import net.casual.bot.utils.CommandUtils
import net.casual.bot.utils.EventEmbeds
import net.casual.bot.utils.ImageUtil
import net.casual.bot.utils.ImageUtil.toFileUpload
import net.casual.bot.utils.Named
import net.casual.bot.utils.capitalize
import net.casual.bot.utils.capitalizeAll
import net.casual.bot.utils.impl.LoadingMessage
import net.casual.database.EventPlayers
import net.casual.database.Events
import net.casual.database.MinigamePlayers
import net.casual.database.Minigames
import net.casual.database.stats.DuelMinigameStats
import net.casual.database.stats.UHCMinigameStats
import net.casual.stat.FormattedStat
import net.casual.util.sum
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.*

object StatCommand: Command {
    override val name = "stat"
    override val description = "Shows a specified player's stats"

    private val minigames = HashMap<String, MinigameStatExpressions>()

    init {
        this.minigames["duels"] = MinigameStatExpressions.of(DuelMinigameStats) {
            stat("kills", DuelMinigameStats.kills.sum())
            stat("damage_dealt", DuelMinigameStats.damageDealt.sum())
            stat("damage_taken", DuelMinigameStats.damageTaken.sum())
            stat("damage_healed", DuelMinigameStats.damageHealed.sum())
            stat("wins", DuelMinigameStats.won.sum())
        }
        this.minigames["uhc"] = MinigameStatExpressions.of(UHCMinigameStats) {
            stat("kills", UHCMinigameStats.kills.sum())
            stat("damage_dealt", UHCMinigameStats.damageDealt.sum())
            stat("damage_taken", UHCMinigameStats.damageTaken.sum())
            stat("damage_healed", UHCMinigameStats.damageHealed.sum())
            lifetimeStat("wins", UHCMinigameStats.won.sum())
            lifetimeStat("deaths", UHCMinigameStats.died.sum())
        }
    }

    override fun build(command: SlashCommandData) {
        command.restrict(true)

        val events = CasualBot.database.getEvents()
        for (minigame in this.minigames.keys) {
            command.subcommand(minigame, "The minigame of the stat you want to display") {
                CommandUtils.addPlayerOption(this)
                CommandUtils.addEventOption(this, events)
            }
        }
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val minigame = command.subcommandName!!

        val (profile, username) = CommandUtils.getMojangProfile(command)
        if (profile == null) {
            loading.replace(EventEmbeds.unknownUsername(username))
            return
        }

        val expressions = this.minigames[minigame]
        if (expressions == null) {
            loading.replace(EventEmbeds.wentWrong("Unable to fetch stats"))
            return
        }

        val event = command.getOption<String>("event")
        val stats = CasualBot.database.transaction {
            getStats(profile.id, event, expressions)
        }

        if (stats.isNullOrEmpty()) {
            loading.replace(EventEmbeds.noStats(profile.name))
            return
        }

        val image = ImageUtil.playerStatsImage(profile.name, profile.id, minigame.capitalize(), stats)
        val file = image.toFileUpload("stats.png")
        loading.replace(attachments = listOf(file))
    }

    private fun getStats(uuid: UUID, event: String?, expressions: MinigameStatExpressions): List<Named<FormattedStat>>? {
        val selected = ArrayList<Named<Expression<*>>>()
        for ((name, expression) in expressions.entries()) {
            val chosen = if (event == null) expression.lifetime else expression.minigame
            if (chosen != null) {
                selected.add(Named(name.capitalizeAll("_"), chosen))
            }
        }
        if (selected.isEmpty()) {
            return null
        }

        val query = expressions.stats.joinedWithEventPlayers().select(selected.map { it.value })
        val filtered = if (event == null) {
            query.where { EventPlayers.uuid eq uuid }
        } else {
            val minigames = Minigames
                .join(Events, JoinType.INNER, additionalConstraint = { Minigames.event eq Events.id })
                .selectAll()
                .where { Events.name eq event }
                .map { it[Minigames.id] }
            query.where { (EventPlayers.uuid eq uuid) and (MinigamePlayers.minigame inList minigames) }
        }

        val row = filtered.groupBy(EventPlayers.uuid).firstOrNull() ?: return null
        return selected.map { (name, expression) ->
            Named(name, FormattedStat.of(row[expression]!!))
        }
    }
}
