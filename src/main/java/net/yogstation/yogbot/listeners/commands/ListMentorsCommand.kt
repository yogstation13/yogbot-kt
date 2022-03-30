package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.util.DiscordUtil
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.sql.PreparedStatement
import java.sql.SQLException

@Component
class ListMentorsCommand(discordConfig: DiscordConfig, private val database: DatabaseManager) : TextCommand(
	discordConfig
) {
	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		try {
			database.byondDbConnection.use { connection ->
				connection.prepareStatement("SELECT ckey FROM `${database.prefix("mentor")}`")
					.use { stmt ->
						return sendMentorsLIst(stmt, event)
					}
			}
		} catch (e: SQLException) {
			logger.error("Error with SQL Query", e)
			return DiscordUtil.reply(event, "An error has occurred")
		}
	}

	private fun sendMentorsLIst(
		stmt: PreparedStatement,
		event: MessageCreateEvent
	): Mono<*> {
		val results = stmt.executeQuery()
		val builder = StringBuilder("Current Mentors:")
		while (results.next()) {
			builder.append("\n")
			builder.append(results.getString("ckey"))
		}
		results.close()
		return DiscordUtil.reply(event, builder.toString())
	}

	override val description = "Get current mentors."
	override val name = "listmentors"
}
