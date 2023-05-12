package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.Yogbot
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import net.yogstation.yogbot.util.StringUtils
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.sql.SQLException
import java.sql.Statement

@Component
class ManageEchelonCommand(discordConfig: DiscordConfig,
						   permissions: PermissionsManager,
						   private val databaseManager: DatabaseManager
) : PermissionsCommand(discordConfig, permissions) {
	override val name = "echelon"
	override val requiredPermissions = "echelon"
	override val description = "Allows a ckey to bypass echelon"

	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		val args = event.message.content.split(" ").toTypedArray()
		return if (args.size < 2) {
			DiscordUtil.reply(event, "Usage is `${args[0]} <add|search|delete>`")
		} else when (args[1]) {
			"add" -> addWhitelist(event, args)
			"search" -> searchWhitelist(event, args)
			"delete" -> removeWhitelist(event, args)
			else -> DiscordUtil.reply(event, "Unknown subcommand: `${args[1]}`")
		}
	}

	private fun addWhitelist(event: MessageCreateEvent, args: Array<String>): Mono<*> {
		if (args.size < 3) {
			return DiscordUtil.reply(event, "Usage is `${args[0]} ${args[1]} <ckey>`")
		}
		databaseManager.byondDbConnection.use {conn -> conn.prepareStatement("""
			INSERT INTO ${databaseManager.prefix("bound_credentials")} (`ckey`, `flags`) VALUES (?, 'allow_proxies')
		""".trimIndent(), Statement.RETURN_GENERATED_KEYS).use { preparedStatement ->
			preparedStatement.setString(1, StringUtils.ckeyIze(args[2]))
			try {
				val rows = preparedStatement.executeUpdate()
				if(rows == 0) throw SQLException("No rows inserted")
			} catch (e: SQLException) {
				return DiscordUtil.reply(event, "An error has occurred: ${e.message}")
			}
			preparedStatement.generatedKeys.use {
				if(!it.next()) return DiscordUtil.reply(event, "No ID returned")
				return DiscordUtil.reply(event, "Created exception ${it.getLong(1)} for `${args[2]}`.")
			}
		}
		}
	}

	private fun searchWhitelist(event: MessageCreateEvent, args: Array<String>): Mono<*> {
		if(args.size < 3) return DiscordUtil.reply(event, "Usage is `${args[0]} ${args[1]} <ckey>`")
		databaseManager.byondDbConnection.use {connection -> connection.prepareStatement(
			"""
				SELECT `id`, `ckey`
				FROM ${databaseManager.prefix("bound_credentials")}
				WHERE ckey = ? AND FIND_IN_SET('allow_proxies', flags)
			""".trimIndent()
		).use { preparedStatement ->
			preparedStatement.setString(1, args[2])
			preparedStatement.executeQuery().use { results ->
				val builder = StringBuilder("Whitelists for ckey `${args[2]}`:\n```\n")
				val monos: MutableList<Mono<*>> = ArrayList();
				var first = true
				while(results.next()) {
					builder.append("${results.getInt("id")}: Ckey ${results.getString("ckey")}\n")
					if(builder.length > Yogbot.MAX_MESSAGE_LENGTH - 10) {
						builder.append("```")
						monos.add(if(first) DiscordUtil.reply(event, builder.toString()) else DiscordUtil.send(event, builder.toString()))
						first = false
						builder.setLength(0)
						builder.append("```")
					}
				}
				builder.append("```")
				monos.add(if(first) DiscordUtil.reply(event, builder.toString()) else DiscordUtil.send(event, builder.toString()))
				return Mono.`when`(monos)
			}
		}
		}
	}

	private fun removeWhitelist(event: MessageCreateEvent, args: Array<String>): Mono<*> {
		if(args.size < 3) return DiscordUtil.reply(event, "Usage is `${args[0]} ${args[1]} <whitelist>`")
		val id = args[2].toIntOrNull() ?: return DiscordUtil.reply(event, "${args[2]} is not a valid integer")
		val monos: MutableList<Mono<*>> = ArrayList()
		databaseManager.byondDbConnection.use {  connection ->
			var oldFlags = ""
			connection.prepareStatement("""
				SELECT `ckey`, `flags`
				FROM ${databaseManager.prefix("bound_credentials")}
				WHERE `id` = ? AND FIND_IN_SET('allow_proxies', flags)
			""".trimIndent()).use { preparedStatement ->
				preparedStatement.setInt(1, id)
				preparedStatement.executeQuery().use {
					if(!it.next()) return DiscordUtil.reply(event, "No binding with id $id found!")
					monos.add(DiscordUtil.reply(event, "Deleted whitelist for ckey `${it.getString("ckey")}`"))
					oldFlags = it.getString("flags")
				}
			}
			if(oldFlags == "allow_proxies") {
				connection.prepareStatement("""
					DELETE FROM ${databaseManager.prefix("bound_credentials")}
					WHERE `id` = ? AND FIND_IN_SET('allow_proxies', flags)
				""".trimIndent()).use { preparedStatement ->
					preparedStatement.setInt(1, id)
					val rows = preparedStatement.executeUpdate()
					if (rows == 0) return DiscordUtil.reply(event, "Unable to delete bond.")
					return Mono.`when`(monos)
				}
			} else {
				val flagsArray = oldFlags.split(",")
				val newFlags = flagsArray.filter { it != "allow_proxies" }.joinToString(",")
				connection.prepareStatement("""
					UPDATE ${databaseManager.prefix("bound_credentials")}
					SET flags = ?
					WHERE `id` = ? AND FIND_IN_SET('allow_proxies', flags)
				""".trimIndent()).use { preparedStatement ->
					preparedStatement.setString(1, newFlags)
					preparedStatement.setInt(2, id)
					val rows = preparedStatement.executeUpdate()
					if(rows == 0) return DiscordUtil.reply(event, "Unable to delete bond.")
					return Mono.`when`(monos)
				}
			}
		}
	}
}
