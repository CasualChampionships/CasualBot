package net.casual.bot.util.impl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamData(
    @SerialName("max_players")
    val maxPlayers: Int = 32,
    val teams: List<Team> = listOf()
) {
    @Serializable
    data class Team(val name: String, val players: List<Player>)

    @Serializable
    data class Player(val username: String, val id: String)
}