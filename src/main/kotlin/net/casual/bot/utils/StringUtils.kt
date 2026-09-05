package net.casual.bot.utils

import java.util.*

fun String.capitalizeAll(delimiter: String = " ", separator: String = " "): String {
    return split(delimiter).joinToString(separator) { it.capitalize() }
}

fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

fun String.toEventName(): String {
    return trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

fun String.toDisplayName(): String {
    return capitalizeAll("_", " ")
}

fun parseHexColour(raw: String?): Int? {
    if (raw.isNullOrBlank()) {
        return null
    }
    val cleaned = raw.trim().removePrefix("#").removePrefix("0x")
    return cleaned.toIntOrNull(16)?.takeIf { it in 0..0xFFFFFF }
}
