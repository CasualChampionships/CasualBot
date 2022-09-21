package util

import java.util.*

object Util {
    fun String.capitaliseAll(): String {
        return split(" ").map { it.capitalise() }.joinToString(" ")
    }

    fun String.capitalise(): String {
        return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}