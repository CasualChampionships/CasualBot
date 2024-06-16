package net.casual.bot.commands

import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.casual.bot.CasualBot
import net.casual.bot.commands.stats.MinigameStatExpressions
import net.casual.bot.util.DatabaseUtils.resolveScoreboard
import net.casual.bot.util.EmbedUtil
import net.casual.bot.util.ImageUtil
import net.casual.bot.util.StringUtil.capitalise
import net.casual.bot.util.StringUtil.capitaliseAll
import net.casual.bot.util.impl.LoadingMessage
import net.casual.database.Events
import net.casual.database.Minigame
import net.casual.database.Minigames
import net.casual.database.stats.DuelMinigameStats
import net.casual.database.stats.MinigameStats
import net.casual.database.stats.UHCMinigameStats
import net.casual.util.sum
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.FileUpload
import org.jetbrains.exposed.sql.*
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ScoreboardCommand: Command {
    override val name = "scoreboard"
    override val description = "Shows the scoreboard for a given stat"

    private val minigames = HashMap<String, MinigameStatExpressions>()

    init {
        provider("duels", DuelMinigameStats) {
            stat("kills", DuelMinigameStats.kills.sum())
            stat("damage_taken", DuelMinigameStats.damageTaken.sum())
            stat("damage_healed", DuelMinigameStats.damageHealed.sum())
            stat("damage_dealt", DuelMinigameStats.damageDealt.sum())

            lifetimeStat("most_kills", DuelMinigameStats.kills.max())
            lifetimeStat("wins", DuelMinigameStats.won.sum())
        }
        provider("uhc", UHCMinigameStats) {
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

    private fun provider(name: String, stats: MinigameStats, body: MinigameStatExpressions.Builder.() -> Unit) {
        val builder = MinigameStatExpressions.Builder()
        builder.body()
        val provider = builder.build(stats)
        minigames[name] = provider
    }

    override fun build(command: SlashCommandData) {
        command.restrict(true)

        val events = CasualBot.database.getEvents()
        for ((minigame, expressions) in minigames) {
            command.subcommand(minigame, "The minigame of the stat you want to display") {
                option<String>("type", "The stat type you want to display", true) {
                    for (type in expressions.types()) {
                        choice(type.capitaliseAll("_"), type)
                    }
                }
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
        val stat = command.getOption<String>("type")!!
        val event = command.getOption<String>("event")

        val expressions = minigames[minigame]
        if (expressions == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Unable to fetch scoreboard")).queue()
            return
        }

        val stats = expressions.get(stat)
        if (stats == null) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("Unable to fetch scoreboard")).queue()
            return
        }

        val scoreboard = if (event != null) {
            if (stats.minigame == null) {
                loading.replace(EmbedUtil.somethingWentWrongEmbed("This stat type doesn't support specific event stats")).queue()
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
                loading.replace(EmbedUtil.somethingWentWrongEmbed("This stat type doesn't support lifetime stats")).queue()
                return
            }
            CasualBot.database.transaction {
                expressions.stats.lifetimeScoreboard(stats.lifetime, limit = 10)
            }
        }

        if (scoreboard.isEmpty()) {
            loading.replace(EmbedUtil.somethingWentWrongEmbed("The scoreboard is empty!")).queue()
            return
        }

        val formatted = CasualBot.database.resolveScoreboard(scoreboard)
        val title = "${minigame.capitalise()}: ${stat.capitaliseAll("_")}"
        val image = ImageUtil.scoreboardImage(title, formatted)
        ByteArrayOutputStream().use { stream ->
            ImageIO.write(image, "png", stream)
            val file = FileUpload.fromData(stream.toByteArray(), "stats.png")
            loading.replace(attachments = listOf(file)).queue()
        }
    }
}
