import config.Config
import database.DataBase
import event.EventHandler
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.internal.utils.JDALogger
import java.nio.file.Path

val LOGGER = JDALogger.getLog("UHC Bot")

val CONFIG by lazy {
    Config.from(Path.of("UHCConfig.json"))
}

val BOT by lazy {
    UHCBot(CONFIG.token, CONFIG.mongoUrl, CONFIG.guildId)
}

fun main() {
    BOT
}

class UHCBot(token: String, mongo: String, guild: Long) {
    val jda = JDABuilder.createDefault(token).apply {
        enableIntents(GatewayIntent.MESSAGE_CONTENT)
        addEventListeners(EventHandler())
    }.build()
    val db = DataBase(mongo)
    val guild by lazy { jda.getGuildById(guild)!! }
}