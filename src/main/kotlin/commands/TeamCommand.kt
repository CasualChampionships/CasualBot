package commands

import BOT
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.bson.Document
import util.CommandUtil
import util.CommandUtil.addServerArgument
import util.CommandUtil.getServer

class TeamCommand: AbstractCommand() {
    override fun getName() = "team"

    override fun getDescription() = "Used for creating and deleting teams"

    override fun buildCommand(command: SlashCommandData) {
        command.restrict(true, Permission.ADMINISTRATOR)
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
    }

    override fun onCommand(event: GenericCommandInteractionEvent) {
        when (event.subcommandName) {
            "create" -> {
                val name = event.getOption<String>("name")!!
                val doc = Document().apply {
                    put("_id", BOT.db.teams.countDocuments() + 1)
                    put("prefix", name)
                    put("name", name)
                    put("members", listOf<Any>())
                    put("logo", "")
                    put("wins", 0)
                    put("colour", "WHITE")
                }
                Regex("\\d+$").find(name)?.value?.toInt()?.let {
                    val roleName = name.replace(Regex("\\d+$"), "")
                    event.guild?.getRolesByName(roleName, true) ?: event.reply("No existing team: $roleName")
                }
                BOT.db.teams.insertOne(doc)
                event.reply("Successfully created team: $name").queue()
                CommandUtil.loadCommands(BOT.jda)
            }
            "delete" -> {
                val (name, _) = event.getServer {
                    event.reply("Invalid server: $it").queue()
                    return
                }
                if (BOT.db.teams.deleteOne(Filters.eq("name", name)).deletedCount == 0L) {
                    event.reply("Failed to delete any team").queue()
                    return
                }
                event.reply("Successfully delete team: $name").queue()
                CommandUtil.loadCommands(BOT.jda)
            }
            "setlogo" -> {
                val (name, _) = event.getServer {
                    event.reply("Invalid server: $it").queue()
                    return
                }
                val url = event.getOption<String>("url") ?: return
                BOT.db.teams.findOneAndUpdate(Filters.eq("name", name), Updates.set("logo", url))
                event.reply("Successfully updated logo").queue()
            }
            "setcolour" -> {
                val (name, _) = event.getServer {
                    event.reply("Invalid server: $it").queue()
                    return
                }
                val colour = event.getOption<String>("colour") ?: return
                BOT.db.teams.findOneAndUpdate(Filters.eq("name", name), Updates.set("colour", colour))
                event.reply("Successfully updated colour").queue()
            }
        }
    }
}