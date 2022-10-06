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
import java.sql.Types

@Component
class BoundCredentialsCommand(discordConfig: DiscordConfig,
							  permissions: PermissionsManager,
							  private val databaseManager: DatabaseManager
) : PermissionsCommand(discordConfig, permissions) {
	override val name = "bcreds"
	override val requiredPermissions = "bcreds"
	override val description = "Manages bound credentials"

	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		val args = event.message.content.split(" ").toTypedArray()
		return if (args.size < 2) {
			DiscordUtil.reply(event, "Usage is `${args[0]} <add|search|delete>`")
		} else when (args[1]) {
			"add" -> addBoundCredential(event, args)
			"search" -> findBoundCredential(event, args)
			"delete" -> removeBoundCredential(event, args)
			else -> DiscordUtil.reply(event, "Unknown subcommand: `${args[1]}`")
		}
	}

	private fun addBoundCredential(event: MessageCreateEvent, args: Array<String>): Mono<*> {
		if (args.size < 5) {
			return DiscordUtil.reply(event, "Usage is `${args[0]} ${args[1]} <ckey> <cid> <ip>`\n" +
				"Use `null` to ignore cid or ip. IP can be ascii or decimal")
		}
		val ipField = if(args[4].toIntOrNull() == null) "INET_ATON(?)" else "?"
		databaseManager.byondDbConnection.use {conn -> conn.prepareStatement("""
			INSERT INTO ${databaseManager.prefix("bound_credentials")} (`ckey`, `computerid`, `ip`) VALUES (?, ?, $ipField)
		""".trimIndent(), Statement.RETURN_GENERATED_KEYS).use { preparedStatement ->
			preparedStatement.setString(1, StringUtils.ckeyIze(args[2]))
			if(args[3] == "null") preparedStatement.setNull(2, Types.VARCHAR) else preparedStatement.setString(2, args[3])
			if(args[4] == "null") preparedStatement.setNull(3, Types.VARCHAR) else preparedStatement.setString(3, args[4])
			try {
				val rows = preparedStatement.executeUpdate()
				if(rows == 0) throw SQLException("No rows inserted")
			} catch (e: SQLException) {
				return DiscordUtil.reply(event, "An error has occurred: ${e.message}")
			}
			preparedStatement.generatedKeys.use {
				if(!it.next()) return DiscordUtil.reply(event, "No ID returned")
				val bindings = (if(args[3] == "null") 0 else 1) + (if(args[4] == "null") 0 else 2)
				return DiscordUtil.reply(event,
					"Created binding ${it.getLong(1)}: ckey `${args[2]}` is now bound to " +
						"${if(bindings and 1 == 1) "cid `${args[3]}`" else ""}${if(bindings == 3) " and " else ""}" +
						if(bindings and 2 == 2) "ip `${args[4]}`" else ""
				)
			}
		}
		}
	}

	private fun findBoundCredential(event: MessageCreateEvent, args: Array<String>): Mono<*> {
		if(args.size < 3) return DiscordUtil.reply(event, "Usage is `${args[0]} ${args[1]} <search term>`\n" +
			"Search is either a ckey, cid, or ip. \n" +
			"Provide only the search criteria and a best guess will be made for the category, " +
			"or explicitly state the category first.\n" +
			"For example `${args[0]} ${args[1]} 127.0.0.1` would search for IP 127.0.0.1, " +
			"while `${args[0]} ${args[1]} ckey 127.0.0.1` would force a search by ckey")
		var searchTerm = ""
		val searchCriteria: String = if(args.size > 3) {
			searchTerm = args[3]
			args[2].lowercase()
		} else {
			searchTerm = args[2]
			if(args[2].toIntOrNull() != null) {
				"cid or ip"
			} else if(args[2].split(".").toTypedArray().size == 4) {
				"ip"
			} else "ckey"
		}
		return when(searchCriteria) {
			"cid" -> findBy(event, "computerid", searchTerm)
			"ip" -> findBy(event, "ip", searchTerm)
			"cid or ip" -> findBy(event, "cid or ip", searchTerm)
			"ckey" -> findBy(event, "ckey", StringUtils.ckeyIze(searchTerm))
			else -> return DiscordUtil.reply(event, "Unknown search criteria `${searchCriteria}`. " +
				"Valid options are `cid`, `ip`, and `ckey`")
		}
	}

	private fun findBy(event: MessageCreateEvent, criteria: String, term: String): Mono<*> {
		val whereClause = when (criteria) {
			"ip" -> "`ip` = ${if(term.toIntOrNull() == null) "INET_ATON(?)" else "?"}"
			"cid or ip" -> "`computerid` = ? or `ip` = ?"
			else -> "`$criteria` = ?"
		}
		databaseManager.byondDbConnection.use {connection -> connection.prepareStatement(
			"""
				SELECT `id`, `ckey`, `computerid`, INET_NTOA(`ip`)
				FROM ${databaseManager.prefix("bound_credentials")}
				WHERE $whereClause
			""".trimIndent()
		).use { preparedStatement ->
			preparedStatement.setString(1, term)
			if(criteria == "cid or ip") preparedStatement.setString(2, term)
			preparedStatement.executeQuery().use { results ->
				val builder = StringBuilder("Bound credentials for $criteria `$term`:\n```\n")
				val monos: MutableList<Mono<*>> = ArrayList();
				var first = true
				while(results.next()) {
					builder.append("${results.getInt("id")}: Ckey ${results.getString("ckey")} is bonded to ")
					builder.append("cid ${results.getString("computerid")} and ")
					builder.append("ip ${results.getString("INET_NTOA(`ip`)")}\n")
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

	private fun removeBoundCredential(event: MessageCreateEvent, args: Array<String>): Mono<*> {
		if(args.size < 3) return DiscordUtil.reply(event, "Usage is `${args[0]} ${args[1]} <bindid>`")
		val id = args[2].toIntOrNull() ?: return DiscordUtil.reply(event, "${args[2]} is not a valid integer")
		val monos: MutableList<Mono<*>> = ArrayList()
		databaseManager.byondDbConnection.use {  connection ->
			connection.prepareStatement("""
				SELECT `ckey`, `computerid`, INET_NTOA(`ip`)
				FROM ${databaseManager.prefix("bound_credentials")}
				WHERE `id` = ?
			""".trimIndent()).use { preparedStatement ->
				preparedStatement.setInt(1, id)
				preparedStatement.executeQuery().use {
					if(!it.next()) return DiscordUtil.reply(event, "No binding with id ${id} found!")
					monos.add(DiscordUtil.reply(event, "Deleted bond: Ckey `${it.getString("ckey")}` bonded to " +
						"cid `${it.getString("computerid")}` and " +
						"ip `${it.getString("INET_NTOA(`ip`)")}`"))
				}
			}
			connection.prepareStatement("""
				DELETE FROM ${databaseManager.prefix("bound_credentials")}
				WHERE `id` = ?
			""".trimIndent()).use { preparedStatement ->
				preparedStatement.setInt(1, id)
				val rows = preparedStatement.executeUpdate()
				if(rows == 0) return DiscordUtil.reply(event, "Unable to delete bond.")
				return Mono.`when`(monos)
			}
		}
	}
}
