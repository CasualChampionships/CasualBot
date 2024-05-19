package config

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.minn.jda.ktx.messages.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import java.nio.file.Files
import java.nio.file.Path

class Config private constructor(
    val path: Path,
    val token: String,
    val mongoUrl: String,
    val guildId: Long,
    val channelIds: JsonObject,
    val dev: Boolean,
    val embeds: MutableMap<String, Embed>,
    val nonTeams: Set<String>
) {
    fun updateEmbeds() {
        embeds.clear()

        val contents = Files.readString(path)
        val json = GSON.fromJson(contents, JsonObject::class.java)
        val embedsJson = json["embeds"].asJsonObject
        for (key in embedsJson.keySet()) {
            embeds[key] = Embed.fromJson(embedsJson[key])
        }
    }

    companion object {
        private val GSON = Gson()

        fun from(path: Path): Config {
            val contents = Files.readString(path)
            val json = GSON.fromJson(contents, JsonObject::class.java)
            val token = json["token"].asString
            val mongo = json["mongoUrl"].asString
            val guildId = json["guildId"].asLong
            val channelIds = json["channelIds"].asJsonObject
            val embedsJson = json["embeds"].asJsonObject
            val dev = json["dev"]?.asBoolean ?: true
            val embeds = LinkedHashMap<String, Embed>()
            for (key in embedsJson.keySet()) {
                embeds[key] = Embed.fromJson(embedsJson[key])
            }
            val teams = HashSet<String>()
            for (key in json["nonTeams"].asJsonArray) {
                teams.add(key.asString)
            }
            return Config(path, token, mongo, guildId, channelIds, dev, embeds, teams)
        }
    }
}

class Embed private constructor(
    val name: String,
    val fields: Map<String, List<String>>,
    val colour: Int,
    val channelId: Long
) {
    fun toEmbed(): MessageEmbed {
        return EmbedBuilder {
            title = name
            val iter = fields.iterator()
            while (iter.hasNext()) {
                val (title, description) = iter.next()
                field {
                    name = title
                    value = description.joinToString(" ").trimEnd() + if (iter.hasNext()) "\n\n_ _" else ""
                    inline = false
                }
//                if (iter.hasNext()) {
//                    field {
//                        name = "_ _"
//                        inline = false
//                    }
//                }
            }
            color = colour
        }.build()
    }

    companion object {
        fun fromJson(element: JsonElement): Embed {
            val jObject = element.asJsonObject
            val embeds = LinkedHashMap<String, List<String>>()
            for (key in jObject.keySet()) {
                val contents = ArrayList<String>()
                val keyElement = jObject[key]
                if (keyElement.isJsonArray) {
                    for (string in jObject[key].asJsonArray) {
                        contents.add(string.asString)
                    }
                    embeds[key] = contents
                }
            }
            val channelId = jObject["channelId"]?.asLong ?: -1
            return Embed(jObject["title"].asString, embeds, jObject["colour"].asInt, channelId)
        }
    }
}