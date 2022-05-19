package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.sql.PreparedStatement
import java.sql.SQLException
import java.util.*

@Component
class TicketCommand(
	discordConfig: DiscordConfig,
	permissions: PermissionsManager,
	private val database: DatabaseManager
) : PermissionsCommand(
	discordConfig, permissions
) {
	override val requiredPermissions = "note"

	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		val args = event.message.content.split(" ").toTypedArray()
		return if (args.size < 2) {
			DiscordUtil.reply(event, "Usage is `${discordConfig.commandPrefix}ticket <help|get>`")
		} else when (args[1]) {
			"help" -> ticketHelp(event, args)
			"get" -> getTicket(event, args)
			else -> DiscordUtil.reply(event, "Unknown subcommand `${args[1]}`")
		}
	}

	private fun ticketHelp(event: MessageCreateEvent, args: Array<String>): Mono<*> {
		if (args.size < 3) {
			return DiscordUtil.reply(
				event,
				String.format(
					"""
					Gets information on a ticket from the database.
					Use `%sticket help <subcommand>` for help on a specific subcommand
					""".trimIndent(),
					discordConfig.commandPrefix
				)
			)
		}
		return if (args[2] == "get") DiscordUtil.reply(
			event,
			String.format(
				Locale.getDefault(),
				"""Gets either the content of a ticket from a specific round, or the list of tickets from that round
	Usage: `%sticket get <round_id> [ticket_id]`
				""",
				discordConfig.commandPrefix
			)
		) else DiscordUtil.reply(event, "Unknown subcommand `${args[1]}`")
	}

	private fun getTicket(event: MessageCreateEvent, args: Array<String>): Mono<*> {
		if (args.size < 3) {
			return DiscordUtil.reply(event, "Usage: `${args[0]} get <round_id> [ticket_id]`")
		}
		val roundId = args[2]
		if (args.size < 4) {
			return getSingleTicket(roundId, event)
		}
		val ticketId = args[3]
		return getRoundTickets(roundId, ticketId, event)
	}

	private fun getRoundTickets(
		roundId: String,
		ticketId: String,
		event: MessageCreateEvent
	): Mono<*> {
		try {
			database.byondDbConnection.use { connection ->
				connection.prepareStatement(
					"""
					SELECT `interactions`.`when`, `interactions`.`user`, `interactions`.`text`
					FROM ${database.prefix("admin_tickets")} as tickets
					JOIN ${database.prefix("admin_ticket_interactions")} interactions on tickets.id = interactions.ticket_id
					WHERE `tickets`.`round_id` = ? AND `tickets`.`ticket_id` = ?;
						"""
				).use { preparedStatement ->
					return processSingleTicket(preparedStatement, roundId, ticketId, event)
				}
			}
		} catch (e: SQLException) {
			logger.error("Error getting ticket", e)
			return DiscordUtil.reply(event, "Failed to get ticket.")
		}
	}

	private fun processSingleTicket(
		preparedStatement: PreparedStatement,
		roundId: String,
		ticketId: String,
		event: MessageCreateEvent
	): Mono<*> {
		preparedStatement.setString(1, roundId)
		preparedStatement.setString(2, ticketId)
		val resultSet = preparedStatement.executeQuery()
		var hasData = false
		var hasSent = false
		val monos: MutableList<Mono<*>> = ArrayList();
		val builder = StringBuilder("Ticket ").append(ticketId)
		builder.append(" for round ").append(roundId).append("\n```\n")

		while (resultSet.next()) {
			hasData = true
			val newline = "${resultSet.getTimestamp("when")}: " +
				"${resultSet.getString("user")}: " +
				"${resultSet.getString("text")}\n"
			if (builder.length + newline.length > 3990) {
				builder.append("```")
				if(hasSent)
					monos.add(DiscordUtil.reply(event, builder.toString()))
				else
					monos.add(DiscordUtil.send(event, builder.toString()))
				hasSent = true;
				builder.setLength(0)
				builder.append("```\n")
			}
			builder.append(newline)
		}
		if (!hasData) return DiscordUtil.reply(
			event,
			"Unable to find ticket $ticketId in round $roundId"
		)
		builder.append("```")
		if(hasSent)
			monos.add(DiscordUtil.reply(event, builder.toString()))
		else
			monos.add(DiscordUtil.send(event, builder.toString()))
		return Mono.`when`(monos);
	}

	private fun getSingleTicket(
		roundId: String,
		event: MessageCreateEvent
	): Mono<*> {
		try {
			database.byondDbConnection.use { connection ->
				connection.prepareStatement(
					"""
						SELECT * FROM (
							SELECT `tickets`.`ticket_id`, `tickets`.`ckey`, `tickets`.`a_ckey`, `interactions`.`text`,
							RANK() OVER (PARTITION BY `tickets`.`ticket_id` ORDER BY `interactions`.`id`) as `rank`
							FROM ${database.prefix("admin_tickets")} as tickets
							JOIN ${database.prefix("admin_ticket_interactions")} interactions on tickets.id = interactions.ticket_id
							WHERE `tickets`.`round_id` = ?
						) as ticket_list WHERE ticket_list.`rank` = 1;
							"""
				).use { lookupStatement ->
					return processRoundTickets(lookupStatement, roundId, event)
				}
			}
		} catch (e: SQLException) {
			logger.error("Failed to get admin tickets", e)
			return DiscordUtil.reply(event, "Failed to get tickets.")
		}
	}

	private fun processRoundTickets(
		lookupStatement: PreparedStatement,
		roundId: String,
		event: MessageCreateEvent
	): Mono<*> {
		lookupStatement.setString(1, roundId)
		val resultSet = lookupStatement.executeQuery()
		var hasData = false
		val builder = StringBuilder("Tickets for round ").append(roundId).append(":\n```\n")
		while (resultSet.next()) {
			hasData = true
			builder.append("#")
			builder.append(resultSet.getString("ticket_id"))
			builder.append(" ")
			builder.append(resultSet.getString("ckey"))
			builder.append(": ")
			builder.append(resultSet.getString("text"))
			builder.append(", ")
			builder.append(resultSet.getString("a_ckey"))
			builder.append("\n")
		}
		resultSet.close()
		if (!hasData) return DiscordUtil.reply(
			event,
			"Failed to get ticket for round $roundId"
		)
		builder.append("```")
		return DiscordUtil.reply(event, builder.toString())
	}

	override val description = "Gets ticket information."
	override val name = "ticket"
}
