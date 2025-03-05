package net.casual.bot.commands.stats

import net.casual.database.stats.MinigameStats
import org.jetbrains.exposed.sql.Expression

class StatExpression(
    val lifetime: Expression<*>?,
    val minigame: Expression<*>?
)

class MinigameStatExpressions(
    val stats: MinigameStats,
    private val expressions: Map<String, StatExpression>
) {
    fun get(name: String): StatExpression? {
        return expressions[name]
    }

    fun types(): Set<String> {
        return this.expressions.keys
    }

    fun entries(): Iterable<Map.Entry<String, StatExpression>> {
        return expressions.entries
    }

    class Builder {
        private val providers = LinkedHashMap<String, StatExpression>()

        fun stat(name: String, expression: Expression<*>) {
            val provider = StatExpression(expression, expression)
            providers[name] = provider
        }

        fun lifetimeStat(name: String, expression: Expression<*>) {
            val provider = StatExpression(expression, null)
            providers[name] = provider
        }

        fun build(stats: MinigameStats): MinigameStatExpressions {
            return MinigameStatExpressions(stats, providers)
        }
    }
}