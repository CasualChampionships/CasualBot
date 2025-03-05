package net.casual.bot.util

object CollectionUtils {
    fun <T> List<T>.concat(other: List<T>): List<T> {
        val list = ArrayList(this)
        list.addAll(other)
        return list
    }
}