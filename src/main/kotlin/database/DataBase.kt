package database

import BOT
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
import org.bson.conversions.Bson
import util.CommandUtil
import util.EmbedUtil
import util.ImageUtil

class DataBase(url: String) {
    val client = MongoClient(MongoClientURI(url))
    val database = client.getDatabase("UHC")
    val teams = database.getCollection("teams")
    val totalStats = database.getCollection("total_player_stats")

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
        teams.updateOne(Filters.eq("name", serverName), Updates.push("members", username))
        return EmbedUtil.addPlayerSuccessEmbed(username, serverName, team, serverColour)
    }

    fun removePlayer(server: String, colour: Int, username: String): MessageEmbed {
        val team = getTeamMembers(server) ?: return EmbedUtil.somethingWentWrongEmbed("Team not found")
        if (username !in team) {
            return EmbedUtil.playerNotInTeamEmbed(username, server, team, colour)
        }
        teams.updateOne(Filters.eq("name", server), Updates.pull("members", username))
        return EmbedUtil.removePlayerSuccessEmbed(username, server, team.filter { it != username }, getTeamLogo(server), colour)
    }

    fun getTeamInfo(server: String, colour: Int): MessageEmbed {
        return EmbedUtil.getTeamInfoEmbed(server, getTeamMembers(server), getTeamLogo(server), colour)
    }

    fun clearTeam(server: String) {
        teams.updateOne(Filters.eq("name", server), Updates.set("members", listOf<Any>()))
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
        return teams.find().sort(Sorts.descending("wins")).map { it["name"].toString() to it["wins"].toString() }.toMap()
    }

    fun getTeamStats(): MessageEmbed {
        return EmbedUtil.winsEmbed(getTeamWins())
    }

    fun getPlayerStats(username: String): Pair<MessageEmbed, FileUpload?> {
        val name = CommandUtil.getCorrectName(username)
        name ?: return EmbedUtil.noStatsEmbed(username) to null
        val result = totalStats.find(Filters.eq("name", name)).first()
        result ?: return EmbedUtil.noStatsEmbed(name) to null
        val imageName = "${username}_stats.png"
        val image = ImageUtil.playerStatsImage(name, result, imageName)
        return EmbedUtil.playerStatsEmbed(name, imageName) to image
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
            val teamName = it.getString("name")
            teamName to (it.getString("role") ?: teamName)
        }.toMap()
    }

    private fun getTeamDocument(teamName: String): Document? {
        val teams = teams.find(Filters.eq("name", teamName))
        return teams.first().also {
            it ?: LOGGER.info("No team with name $teamName")
        }
    }

    private fun getPlayerTeam(username: String): String? {
        for (team in teams.find()) {
            val members = team.getList("members", String::class.java)
            for (member in members) {
                if (username == member) {
                    return team.getString("name")
                }
            }
        }
        return null
    }
}