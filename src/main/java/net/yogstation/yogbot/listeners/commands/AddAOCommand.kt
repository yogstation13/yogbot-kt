package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.sql.SQLException

/**
 * Adds an AO based on discord tag or ckey
 */
@Component
class AddAOCommand(discordConfig: DiscordConfig, permissions: PermissionsManager, database: DatabaseManager) :
	EditRankCommand(
		discordConfig, permissions, database
	) {
	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		val target = getTarget(event)
			?: return DiscordUtil.reply(
				event,
				"Correct usage: `${discordConfig.commandPrefix}addao <ckey or @Username>`"
			)
		return try {
			giveRank(event, target)
		} catch (e: SQLException) {
			logger.error("Error in AddAOCommand", e)
			DiscordUtil.reply(event, "Unable to access database.")
		}
	}

	@Throws(SQLException::class)
	private fun giveRank(
		event: MessageCreateEvent,
		target: CommandTarget
	): Mono<*> {
		database.byondDbConnection.use { connection ->

			connection.prepareStatement(
				"SELECT ckey FROM `${database.prefix("admin")}` WHERE `ckey` = ?;"
			).use { adminCheckStmt ->

				connection.prepareStatement(
					"INSERT INTO `${database.prefix("admin")}` (`ckey`, `rank`) VALUES (?, 'Admin Observer');"
				).use { adminSetStmt ->
					return giveRank(
						event,
						target,
						adminCheckStmt,
						adminSetStmt,
						discordConfig.aoRole
					)
				}
			}
		}
	}

	override val name = "addao"
	override val requiredPermissions = "addao"
	override val description = "Give a user AO rank."
}
