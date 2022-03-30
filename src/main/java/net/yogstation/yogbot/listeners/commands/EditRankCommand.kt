package net.yogstation.yogbot.listeners.commands

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import reactor.core.publisher.Mono
import java.sql.PreparedStatement
import java.sql.SQLException

/**
 * Command for editing the in game rank of someone, such as add/remove ao
 */
abstract class EditRankCommand(
	discordConfig: DiscordConfig,
	permissions: PermissionsManager,
	protected val database: DatabaseManager
) : PermissionsCommand(
	discordConfig, permissions
) {
	/**
	 * Gives the provided role to the target, checking to see if they already have an in game rank.
	 * @param event The message create event, used for replies
	 * @param target The target of the command, will be populated
	 * @param rankCheckStmt The prepared statement, that should return no rows if parameter 1 should be given the rank
	 * @param rankSetStmt Gives rank to parameter 1
	 * @param role The role to give
	 */
	@Throws(SQLException::class)
	protected fun giveRank(
		event: MessageCreateEvent,
		target: CommandTarget,
		rankCheckStmt: PreparedStatement,
		rankSetStmt: PreparedStatement,
		role: Long
	): Mono<*> {
		val errors = target.populate(database)
		if (errors != null) return DiscordUtil.reply(event, errors)
		val snowflake: Snowflake = target.snowflake ?: return DiscordUtil.reply(event, "Unable to get discord id")

		var result: Mono<*> = Mono.empty<Any>()
		rankCheckStmt.setString(1, target.ckey)
		val rankCheckResults = rankCheckStmt.executeQuery()
		result = if (rankCheckResults.next()) {
			result.and(DiscordUtil.send(event, "User already has in-game rank."))
		} else {
			rankSetStmt.setString(1, target.ckey)
			rankSetStmt.execute()
			if (rankSetStmt.updateCount > 0) {
				result.and(DiscordUtil.send(event, "In game rank given successfully"))
			} else {
				result.and(DiscordUtil.send(event, "Failed to give in game rank"))
			}
		}
		rankCheckResults.close()

		return result.and(
			event.guild
				.flatMap { guild: Guild ->
					guild.getMemberById(snowflake)
						.flatMap { member: Member ->
							member.addRole(Snowflake.of(role))
								.and(DiscordUtil.send(event, "Added discord role"))
						}
				}
		)
	}

	/**
	 * Removes role and rank from target
	 * @param event The message create event, used for replies
	 * @param target The target of the command, will be populated
	 * @param rankSetStmt Removes all rank from parameter 1
	 * @param role The role to give
	 */
	@Throws(SQLException::class)
	protected fun removeRank(
		event: MessageCreateEvent,
		target: CommandTarget,
		rankSetStmt: PreparedStatement,
		role: Long
	): Mono<*> {
		val errors = target.populate(database)
		if (errors != null) return DiscordUtil.reply(event, errors)
		val snowflake: Snowflake = target.snowflake ?: return DiscordUtil.reply(event, "Unable to get discord id")
		var result: Mono<*> = Mono.empty<Any>()
		rankSetStmt.setString(1, target.ckey)
		rankSetStmt.execute()
		result = if (rankSetStmt.updateCount > 0) {
			result.and(DiscordUtil.send(event, "In game rank removed successfully"))
		} else {
			result.and(DiscordUtil.send(event, "Failed to remove in game rank"))
		}
		return result.and(
			event.guild
				.flatMap { guild: Guild ->
					guild.getMemberById(snowflake)
						.flatMap { member: Member ->
							member.removeRole(Snowflake.of(role))
								.and(DiscordUtil.send(event, "Removed discord role"))
						}
				}
		)
	}
}
