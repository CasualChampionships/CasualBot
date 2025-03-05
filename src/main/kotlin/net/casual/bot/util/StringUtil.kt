package net.casual.bot.util

import java.util.*

object StringUtil {
    fun String.capitaliseAll(delimiter: String = " ", separator: String = " "): String {
        return split(delimiter).joinToString(separator) { it.capitalise() }
    }

    fun String.capitalise(): String {
        return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}