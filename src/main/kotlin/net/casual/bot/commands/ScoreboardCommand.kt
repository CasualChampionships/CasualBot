package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.casual.bot.CasualBot
import net.casual.bot.commands.stats.MinigameStatExpressions
import net.casual.bot.utils.CommandUtils
import net.casual.bot.utils.DatabaseUtils.resolveScoreboard
import net.casual.bot.utils.EventEmbeds
import net.casual.bot.utils.ImageUtil
import net.casual.bot.utils.ImageUtil.toFileUpload
import net.casual.bot.utils.capitalize
import net.casual.bot.utils.capitalizeAll
import net.casual.bot.utils.impl.LoadingMessage
import net.casual.database.Events
import net.casual.database.Minigame
import net.casual.database.Minigames
import net.casual.database.stats.DuelMinigameStats
import net.casual.database.stats.UHCMinigameStats
import net.casual.util.sum
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.selectAll

object ScoreboardCommand: Command {
    override val name = "scoreboard"
    override val description = "Shows the scoreboard for a given stat"

    private val minigames = HashMap<String, MinigameStatExpressions>()

    init {
        this.minigames["duels"] = MinigameStatExpressions.of(DuelMinigameStats) {
            stat("kills", DuelMinigameStats.kills.sum())
            stat("damage_taken", DuelMinigameStats.damageTaken.sum())
            stat("damage_healed", DuelMinigameStats.damageHealed.sum())
            stat("damage_dealt", DuelMinigameStats.damageDealt.sum())

            lifetimeStat("most_kills", DuelMinigameStats.kills.max())
            lifetimeStat("wins", DuelMinigameStats.won.sum())
        }
        this.minigames["uhc"] = MinigameStatExpressions.of(UHCMinigameStats) {
            stat("kills", UHCMinigameStats.kills.sum())
            stat("damage_taken", UHCMinigameStats.damageTaken.sum())
            stat("damage_healed", UHCMinigameStats.damageHealed.sum())
            stat("damage_dealt", UHCMinigameStats.damageDealt.sum())
            stat("heads_consumed", UHCMinigameStats.headsConsumed.sum())
            stat("time_alive", UHCMinigameStats.aliveTime.sum())
            stat("time_crouched", UHCMinigameStats.crouchTime.sum())
            stat("jumps", UHCMinigameStats.jumps.sum())
            stat("relogs", UHCMinigameStats.relogs.sum())
            stat("blocks_placed", UHCMinigameStats.blocksPlaced.sum())
            stat("blocks_mined", UHCMinigameStats.blocksMined.sum())

            lifetimeStat("most_kills", UHCMinigameStats.kills.max())
            lifetimeStat("deaths", UHCMinigameStats.died.sum())
            lifetimeStat("wins", UHCMinigameStats.won.sum())
        }
    }

    override fun build(command: SlashCommandData) {
        command.restrict(true)

        val events = CasualBot.database.getEvents()
        for ((minigame, expressions) in this.minigames) {
            command.subcommand(minigame, "The minigame of the stat you want to display") {
                option<String>("type", "The stat type you want to display", true) {
                    for (type in expressions.types()) {
                        choice(type.capitalizeAll("_"), type)
                    }
                }
                CommandUtils.addEventOption(this, events)
            }
        }
    }

    override suspend fun execute(command: GenericCommandInteractionEvent, loading: LoadingMessage) {
        val minigame = command.subcommandName!!
        val stat = command.getOption<String>("type")!!
        val event = command.getOption<String>("event")

        val expressions = this.minigames[minigame]
        val stats = expressions?.get(stat)
        if (expressions == null || stats == null) {
            loading.replace(EventEmbeds.wentWrong("Unable to fetch scoreboard"))
            return
        }

        val scoreboard = if (event != null) {
            if (stats.minigame == null) {
                loading.replace(EventEmbeds.wentWrong("This stat type doesn't support specific event stats"))
                return
            }
            CasualBot.database.transaction {
                val minigames = Minigame.wrapRows(
                    Minigames.join(Events, JoinType.INNER, additionalConstraint = { Minigames.event eq Events.id })
                        .selectAll()
                        .where { Events.name eq event }
                )
                expressions.stats.scoreboard(minigames, stats.minigame, limit = 10)
            }
        } else {
            if (stats.lifetime == null) {
                loading.replace(EventEmbeds.wentWrong("This stat type doesn't support lifetime stats"))
                return
            }
            CasualBot.database.transaction {
                expressions.stats.lifetimeScoreboard(stats.lifetime, limit = 10)
            }
        }

        if (scoreboard.isEmpty()) {
            loading.replace(EventEmbeds.wentWrong("The scoreboard is empty!"))
            return
        }

        val formatted = CasualBot.database.resolveScoreboard(scoreboard)
        val title = "${minigame.capitalize()}: ${stat.capitalizeAll("_")}"
        val image = ImageUtil.scoreboardImage(title, formatted)
        val file = image.toFileUpload("stats.png")
        loading.replace(attachments = listOf(file))
    }
}
