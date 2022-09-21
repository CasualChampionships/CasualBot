package database

import LOGGER
import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import dev.minn.jda.ktx.messages.Embed
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.utils.FileUpload
import org.bson.Document
import util.CommandUtil
import util.EmbedUtil
import util.ImageUtil

class DataBase(url: String) {
    val client = MongoClient(MongoClientURI(url))
    val database = client.getDatabase("UHC")
    val teams = database.getCollection("teams")
    val totalStats = database.getCollection("total_player_stats")

    fun addPlayer(server: Role, username: String): MessageEmbed {
        getPlayerTeam(username)?.let {
            return EmbedUtil.playerTakenEmbed(
                username, it, getTeamMembers(it), getTeamLogo(it), server.colorRaw
            )
        }
        val team = getTeamMembers(server.name) ?: return EmbedUtil.somethingWentWrongEmbed("Team not found")
        if (team.size >= 5) {
            return EmbedUtil.fullTeamEmbed(server.name, team, getTeamLogo(server.name), server.colorRaw)
        }
        teams.updateOne(Filters.eq("name", server.name), Updates.push("members", username))
        return EmbedUtil.addPlayerSuccessEmbed(username, server.name, team, server.colorRaw)
    }

    fun removePlayer(server: Role, username: String): MessageEmbed {
        val team = getTeamMembers(server.name) ?: return EmbedUtil.somethingWentWrongEmbed("Team not found")
        if (username !in team) {
            return EmbedUtil.playerNotInTeamEmbed(username, server.name, team, server.colorRaw)
        }
        teams.updateOne(Filters.eq("name", server.name), Updates.pull("members", username))
        return EmbedUtil.removePlayerSuccessEmbed(username, server.name, team.filter { it != username }, getTeamLogo(server.name), server.colorRaw)
    }

    fun getTeamInfo(server: Role): MessageEmbed {
        return EmbedUtil.getTeamInfoEmbed(server.name, getTeamMembers(server.name), getTeamLogo(server.name), server.colorRaw)
    }

    fun clearTeam(server: Role) {
        teams.updateOne(Filters.eq("name", server.name), Updates.set("members", listOf<Any>()))
    }

    fun getTeamMembers(teamName: String): List<String>? {
        val team = getTeamDocument(teamName) ?: return null
        return team.getList("members", String::class.java)
    }

    fun getTeamLogo(teamName: String): String? {
        val team = getTeamDocument(teamName) ?: return null
        return team.getString("logo")
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