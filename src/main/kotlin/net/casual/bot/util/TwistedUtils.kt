package net.casual.bot.util

object TwistedUtils {
    fun isTwistedDatabase(dbName: String): Boolean {
        return dbName != "casual_championships" && dbName != "casual_championships_debug"
    }
}