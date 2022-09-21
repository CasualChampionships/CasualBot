package config

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

class Config private constructor(
    val token: String,
    val mongoUrl: String,
    val guildId: Long,
    val embeds: Map<String, Embed>,
    val nonTeams: Set<String>
) {
    companion object {
        private val GSON = Gson()

        fun from(path: Path): Config {
            val contents = Files.readString(path)
            val json = GSON.fromJson(contents, JsonObject::class.java)
            val token = json["token"].asString
            val mongo = json["mongoUrl"].asString
            val guildId = json["guildId"].asLong
            val embedsJson = json["embeds"].asJsonObject
            val embeds = HashMap<String, Embed>()
            for (key in embedsJson.keySet()) {
                embeds[key] = Embed.fromJson(embedsJson[key])
            }
            val teams = HashSet<String>()
            for (key in json["nonTeams"].asJsonArray) {
                teams.add(key.asString)
            }
            return Config(token, mongo, guildId, embeds, teams)
        }
    }
}

class Embed private constructor(
    val embeds: Map<String, List<String>>,
    val colour: Int
) {
    companion object {
        fun fromJson(element: JsonElement): Embed {
            val jObject = element.asJsonObject
            val embeds = HashMap<String, List<String>>()
            for (key in jObject.keySet()) {
                val contents = ArrayList<String>()
                for (string in jObject[key].asJsonArray) {
                    contents.add(string.asString)
                }
                embeds[key] = contents
            }
            return Embed(embeds, jObject["colour"].asInt)
        }
    }
}