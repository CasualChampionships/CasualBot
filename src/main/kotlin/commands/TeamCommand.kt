package commands

import BOT
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.bson.Document
import util.CommandUtil
import util.CommandUtil.addPlayerArgument
import util.CommandUtil.addServerArgument
import util.CommandUtil.getPlayerOr
import util.CommandUtil.getServerOr
import util.EmbedUtil

class TeamCommand: AbstractCommand() {
    private val adminOnly = setOf("create", "delete", "setlogo", "setcolour")

    override val name = "team"

    override fun getDescription() = "Used for creating and deleting teams"

    override fun buildCommand(command: SlashCommandData) {
        command.subcommand("create", "Creates a new team") {
            option<String>("name", "The team name", true)
        }
        command.subcommand("delete", "Deletes a team") {
            addServerArgument()
        }
        command.subcommand("setlogo", "Sets the logo for a team") {
            addServerArgument()
            option<String>("url", "The url for the logo", true)
        }
        command.subcommand("setcolour", "Sets the colour for a team") {
            addServerArgument()
            option<String>("colour", "The colour", true) {
                choice("Red", "RED")
                choice("Dark Red", "DARK_RED")
                choice("Gold", "GOLD")
                choice("Yellow", "YELLOW")
                choice("Dark Green", "DARK_GREEN")
                choice("Green", "GREEN")
                choice("Aqua", "AQUA")
                choice("Dark Aqua", "DARK_AQUA")
                choice("Dark Blue", "DARK_BLUE")
                choice("Blue", "BLUE")
                choice("Light Purple", "LIGHT_PURPLE")
                choice("Dark Purple", "DARK_PURPLE")
                choice("White", "WHITE")
                choice("Gray", "GRAY")
                choice("Dark Gray", "DARK_GRAY")
                choice("Black", "BLACK")
            }
        }
        command.subcommand("clear", "Clears all the players from a team") {
            addServerArgument()
        }
        command.subcommand("clearall", "Clears all the players from all teams")
        command.subcommand("add", "adds a player to a specified team") {
            addServerArgument()
            addPlayerArgument()
        }
        command.subcommand("remove", "Removes a player from a specified team") {
            addServerArgument()
            addPlayerArgument()
        }
        command.subcommand("info", "Get a team's current information") {
            addServerArgument()
        }
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        if (event.subcommandName in adminOnly && !CommandUtil.isMemberAdmin(event)) {
            event.replyEmbeds(EmbedUtil.noPermission()).queue()
            return
        }
        when (event.subcommandName) {
            "create" -> {
                val name = event.getOption<String>("name")!!
                val doc = Document().apply {
                    put("_id", name)
                    put("prefix", name)
                    put("members", listOf<Any>())
                    put("logo", "")
                    put("wins", 0)
                    put("colour", "WHITE")
                }
                Regex("\\d+$").find(name)?.value?.toInt()?.let {
                    val regex = if (name.matches(Regex("^\\d+$"))) "_\\d+$" else "\\d+$"
                    val roleName = name.replace(Regex(regex), "")
                    event.guild?.getRolesByName(roleName, true) ?: event.reply("No existing team: $roleName")
                }
                BOT.db.teams.insertOne(doc)
                event.reply("Successfully created team: $name").queue()
                CommandUtil.loadCommands(BOT.jda)
            }
            "delete" -> {
                val (name, _) = event.getServerOr {
                    if (BOT.db.teams.deleteOne(Filters.eq("name", it)).deletedCount == 0L) {
                        return event.reply("Failed to delete any team").queue()
                    }
                    return event.reply("Invalid role: $it, but successfully deleted team from database").queue()
                }
                if (BOT.db.teams.deleteOne(Filters.eq("name", name)).deletedCount == 0L) {
                    return event.reply("Failed to delete any team").queue()
                }
                event.reply("Successfully delete team: $name").queue()
                CommandUtil.loadCommands(BOT.jda)
            }
            "setlogo" -> {
                val (name, _) = event.getServerOr {
                    return event.reply("Invalid server: $it").queue()
                }
                val url = event.getOption<String>("url") ?: return
                BOT.db.teams.findOneAndUpdate(Filters.eq("name", name), Updates.set("logo", url))
                event.reply("Successfully updated logo").queue()
            }
            "setcolour" -> {
                val (name, _) = event.getServerOr {
                    event.reply("Invalid server: $it").queue()
                    return
                }
                val colour = event.getOption<String>("colour") ?: return
                BOT.db.teams.findOneAndUpdate(Filters.eq("name", name), Updates.set("colour", colour))
                event.reply("Successfully updated colour").queue()
            }
            "clear" -> {
                val (name, role) = event.getServerOr {
                    return event.replyEmbeds(EmbedUtil.somethingWentWrongEmbed("$it could not be found?!")).queue()
                }
                if (!CommandUtil.canMemberModifyTeam(event, role)) {
                    return event.replyEmbeds(EmbedUtil.noPermission()).queue()
                }
                BOT.db.clearTeam(name)
                event.reply("Successfully cleared $name").queue()
            }
            "clearall" -> {
                if (!CommandUtil.isMemberAdmin(event)) {
                    event.replyEmbeds(EmbedUtil.noPermission()).queue()
                    return
                }
                BOT.db.clearAllTeams()
                event.reply("Successfully cleared all teams").queue()
            }
            "add" -> {
                val (name, role) = event.getServerOr {
                    event.replyEmbeds(EmbedUtil.somethingWentWrongEmbed("$it could not be found?!")).queue()
                    return
                }

                if (!CommandUtil.canMemberModifyTeam(event, role)) {
                    event.replyEmbeds(EmbedUtil.noPermission()).queue()
                    return
                }

                val (username, _) = event.getPlayerOr {
                    event.replyEmbeds(EmbedUtil.somethingWentWrongEmbed("$it is not a valid username")).queue()
                    return
                }

                event.replyEmbeds(BOT.db.addPlayer(name, role.colorRaw, username)).queue()
            }
            "remove" -> {
                val (name, role) = event.getServerOr {
                    event.replyEmbeds(EmbedUtil.somethingWentWrongEmbed("$it could not be found?!")).queue()
                    return
                }
                if (!CommandUtil.canMemberModifyTeam(event, role)) {
                    event.replyEmbeds(EmbedUtil.noPermission()).queue()
                    return
                }

                val (username, _) = event.getPlayerOr {
                    event.replyEmbeds(EmbedUtil.somethingWentWrongEmbed("$it is not a valid username")).queue()
                    return
                }

                event.replyEmbeds(BOT.db.removePlayer(name, role.colorRaw, username)).queue()
            }
            "info" -> {
                val (name, role) = event.getServerOr {
                    event.replyEmbeds(EmbedUtil.somethingWentWrongEmbed("$it could not be found?!")).queue()
                    return
                }

                event.replyEmbeds(BOT.db.getTeamInfo(name, role.colorRaw)).queue()
            }
        }
    }
}