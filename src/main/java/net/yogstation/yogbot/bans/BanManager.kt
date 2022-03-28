package net.yogstation.yogbot.bans

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Member
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.data.BanRepository
import net.yogstation.yogbot.data.entity.Ban
import net.yogstation.yogbot.util.LogChannel
import net.yogstation.yogbot.util.YogResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.domain.Specification.where
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.sql.Date
import java.sql.SQLException
import java.time.LocalDateTime

/**
 * Handles banning and unbanning people, although ban expiration is handled by RoleUpdater
 */
@Component
class BanManager(
	val client: GatewayDiscordClient,
	val discordConfig: DiscordConfig,
	private val banRepository: BanRepository,
	private val logChannel: LogChannel
) {
	private val logger: Logger = LoggerFactory.getLogger(this::class.java)
	private val softbanRole = Snowflake.of(discordConfig.softBanRole)

	/**
	 * Issues a ban for a member
	 * @param member The member to ban
	 * @param reason The text reason for the ban, it will be sent to the user and logged
	 * @param duration The duration of the ban in minutes, zero or negative values provide indefinite bans
	 * @param author The person who issued the ban, for logging purposes
	 */
	fun ban(member: Member, reason: String, duration: Int, author: String): YogResult<Mono<*>?, String?> {

		val banMessage: StringBuilder = StringBuilder("You have been banned from ")
		banMessage.append(discordConfig.serverName)
		banMessage.append(" for `")
		banMessage.append(reason)
		banMessage.append("` It will ")
		if (duration > 0) {
			banMessage.append("expire in ")
			banMessage.append(duration)
			banMessage.append(" minutes.")
		} else {
			banMessage.append("not expire.")
		}

		val date: Date? = if(duration > 0)
			Date(System.currentTimeMillis() + (duration.toLong() * 60000))
		else null

		banRepository.save(Ban(discordId = member.id.asLong(), reason = reason, expiresAt = date))


		return YogResult.success(
			member.addRole(
				softbanRole,
				"${if (duration <= 0) "Permanent" else "Temporary"} softban by $author for ${reason.trim()}"
			)
				.and(member.privateChannel
					.flatMap { privateChannel -> privateChannel.createMessage(banMessage.toString()) }).and(
					logChannel.log("${member.username} was banned ${if (duration <= 0) "permanently" else "for $duration minutes"} by $author for $reason")
				)
		)
	}

	/**
	 * Revokes all bans held by a member
	 * @param member The member to unban
	 * @param reason The reason for the unban
	 * @param user The user who removed the ban
	 */
	fun unban(member: Member, reason: String, user: String): YogResult<Mono<*>?, String?> {
		val bans: List<Ban> = banRepository.findAll(where(Ban.isBanFor(member.id.asLong())).and(Ban.isBanActive()))
		val now = Date(System.currentTimeMillis())
		bans.forEach { it.revokedAt = now }
		banRepository.saveAll(bans)

		return YogResult.success(
			member.removeRole(
				softbanRole,
				"Unbanned by $user for $reason"
			).and(logChannel.log("${member.username} was unbanned by $user for $reason"))
		)
	}

	/**
	 * Checks to see if a user is banned when they log in, then reapplies the ban role if they are
	 * @param member The member to check
	 */
	fun onLogin(member: Member): Mono<*> {
		return if(banRepository.count(where(Ban.isBanActive()).and(Ban.isBanFor(member.id.asLong()))) > 0) {
			logChannel.log("${member.displayName} is banned, reapplying the ban role")
				.and(member.addRole(softbanRole))
		} else {
			Mono.empty<Any>()
		}
	}
}
