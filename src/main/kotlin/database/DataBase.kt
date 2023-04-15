package database

import CONFIG
import LOGGER
import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.FileUpload
import org.bson.BsonDocument
import org.bson.Document
import util.CommandUtil
import util.EmbedUtil
import util.ImageUtil

class DataBase(url: String) {
    val client = MongoClient(MongoClientURI(url))
    val database = client.getDatabase(if (CONFIG.dev) "TestUHC" else "CasualUHC")
    val teams = database.getCollection("teams")
    val latestStats = database.getCollection("last_player_stats")
    val totalStats = database.getCollection("combined_player_stats")

    fun addPlayer(serverName: String, serverColour: Int, username: String): MessageEmbed {
        getPlayerTeam(username)?.let {
            return EmbedUtil.playerTakenEmbed(
                username, it, getTeamMembers(it), getTeamLogo(it), serverColour
            )
        }
        val team = getTeamMembers(serverName) ?: return EmbedUtil.somethingWentWrongEmbed("Team not found")
        if (team.size >= 5) {
            return EmbedUtil.fullTeamEmbed(serverName, team, getTeamLogo(serverName), serverColour)
        }
        teams.updateOne(Filters.eq("_id", serverName), Updates.push("members", username))
        return EmbedUtil.addPlayerSuccessEmbed(username, serverName, team, serverColour)
    }

    fun removePlayer(server: String, colour: Int, username: String): MessageEmbed {
        val team = getTeamMembers(server) ?: return EmbedUtil.somethingWentWrongEmbed("Team not found")
        if (username !in team) {
            return EmbedUtil.playerNotInTeamEmbed(username, server, team, colour)
        }
        teams.updateOne(Filters.eq("_id", server), Updates.pull("members", username))
        return EmbedUtil.removePlayerSuccessEmbed(username, server, team.filter { it != username }, getTeamLogo(server), colour)
    }

    fun getTeamInfo(server: String, colour: Int): MessageEmbed {
        return EmbedUtil.getTeamInfoEmbed(server, getTeamMembers(server), getTeamLogo(server), colour)
    }

    fun clearTeam(server: String) {
        teams.updateOne(Filters.eq("_id", server), Updates.set("members", listOf<Any>()))
    }

    fun clearAllTeams() {
        teams.updateMany(BsonDocument(), Updates.set("members", listOf<Any>()))
    }

    fun getTeamMembers(teamName: String): List<String>? {
        val team = getTeamDocument(teamName) ?: return null
        return team.getList("members", String::class.java)
    }

    fun getTeamLogo(teamName: String): String? {
        val team = getTeamDocument(teamName) ?: return null
        val logo = team.getString("logo")
        return logo.ifEmpty { null }
    }

    fun getTeamWins(): Map<String, String> {
        return teams.find().sort(Sorts.descending("wins")).map { it["_id"].toString() to it["wins"].toString() }.toMap()
    }

    fun getTeamStats(): MessageEmbed {
        return EmbedUtil.winsEmbed(getTeamWins())
    }

    fun getPlayerStats(username: String, uuid: String, lifetime: Boolean = false): Pair<List<MessageEmbed>, List<FileUpload>> {
        if (lifetime) {
            val result = totalStats.find(Filters.eq("_id", uuid)).first()
            result ?: return listOf(EmbedUtil.noStatsEmbed(username)) to listOf()
            val statsImageName = "${username}_stats.png"
            result.remove("_id")
            val statsImage = ImageUtil.playerStatsImage(username, result, statsImageName, true)
            return listOf(EmbedUtil.playerStatsEmbed(username, statsImageName, true)) to listOf(statsImage)
        }

        val result = latestStats.find(Filters.eq("_id", uuid)).first()
        result ?: return listOf(EmbedUtil.noStatsEmbed(username)) to listOf()
        val statsImageName = "${username}_stats.png"
        result.remove("_id")
        result.remove("participated")
        val advancements = (result.remove("advancements") as ArrayList<*>).stream()
            .map { o -> (o as Document) }
            .filter { o -> !o.getString("id").equals("uhc:root") }
            .map { o -> o.getString("title") to o.getString("item") }
            .toList()
            .toMap()

        val statsImage = ImageUtil.playerStatsImage(username, result, statsImageName, false)
        val embeds = mutableListOf(EmbedUtil.playerStatsEmbed(username, statsImageName, false))
        val files = mutableListOf(statsImage)

        val advancementsImageName = "${username}_advancements.png"
        val advancementsImage = ImageUtil.playerAdvancementsImage(username, advancements, advancementsImageName)
        embeds.add(EmbedUtil.playerAdvancementsEmbed(advancementsImageName))
        files.add(advancementsImage)
        return embeds to files
    }

    fun getScoreboard(stat: String, show: Boolean): Pair<MessageEmbed, FileUpload?> {
        val board = totalStats.find()
            .sort(Sorts.descending(stat))
            .filter(Filters.gt(stat, 0))
            .limit(if (show) 0 else 10)
            .toList()
        val imageName = "scoreboard.png"
        val image = ImageUtil.scoreboardImage(stat, board, imageName)
        return EmbedUtil.scoreboardEmbed(stat, imageName) to image
    }

    fun getTeams(): Map<String, String> {
        return teams.find().map {
            val teamName = it.getString("_id")
            teamName to (it.getString("role") ?: teamName)
        }.toMap()
    }

    private fun getTeamDocument(teamName: String): Document? {
        val teams = teams.find(Filters.eq("_id", teamName))
        return teams.first().also {
            it ?: LOGGER.info("No team with name $teamName")
        }
    }

    private fun getPlayerTeam(username: String): String? {
        for (team in teams.find()) {
            val members = team.getList("members", String::class.java)
            for (member in members) {
                if (username == member) {
                    return team.getString("_id")
                }
            }
        }
        return null
    }
}