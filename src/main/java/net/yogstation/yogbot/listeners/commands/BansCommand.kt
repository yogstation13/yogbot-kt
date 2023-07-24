package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.entity.channel.MessageChannel
import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.data.BanRepository
import net.yogstation.yogbot.data.entity.Ban
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import net.yogstation.yogbot.util.MonoCollector
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.text.DateFormat
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Component
class BansCommand(discordConfig: DiscordConfig,
				  permissions: PermissionsManager,
				  private val database: DatabaseManager,
				  private val banRepository: BanRepository) :
	PermissionsCommand(
		discordConfig, permissions
	) {
	override val requiredPermissions = "ban"

	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		val target = getTarget(event)
		var bans = listOf("An unknown error has occurred")
		if (target == null) bans = listOf(
			"Usage is `${discordConfig.commandPrefix}bans <ckey or @Username>`"
		) else {
			if(target.snowflake == null) {
				val result = target.populate(database)
				if(result != null) {
					bans = listOf(result)
				}
			}
			val targetID = target.snowflake
			if (targetID != null) {
				val dateFormat = DateFormat.getInstance();
				val banStrings = banRepository.findAll(Specification.where(Ban.isBanFor(targetID.asLong())))
					.sortedByDescending { it.issuedAt }
					.map {
					val duration = if(it.expiresAt == null) {
						Duration.INFINITE
					} else {
						it.issuedAt.toInstant().until(it.expiresAt!!.toInstant(), ChronoUnit.SECONDS).seconds
					}
					val revoked = it.revokedAt != null && it.revokedAt!!.before(Date())
					val expired = it.expiresAt != null && it.expiresAt!!.before(Date())
					val activeMark = if(revoked || expired) "+" else "-"
					val endReason = if(revoked) "(revoked)" else if (expired) "(expired)" else ""
					val issuedAt = dateFormat.format(it.issuedAt)
					val durationString = if(duration.isInfinite()) {
						"permanently"
					} else {
						"for ${duration.toComponents { days, hours, minutes, seconds, _ ->
							val minuteString = minutes.toString().padStart(2, '0')
							val secondsString = seconds.toString().padStart(2, '0')
							if(days == 0L) {
								"$hours:$minuteString:$secondsString"
							} else {
								"$days days, $hours:$minuteString:$secondsString"
							}
						}}"
					}
					"$activeMark ($issuedAt): Banned $durationString. Reason: ${it.reason}${endReason}"
				}

				bans = DiscordUtil.toMessages(banStrings, "```diff\n", "```")
			}
		}
		val finalBans = bans
		return event.message
			.channel
			.flatMap { messageChannel: MessageChannel ->
				finalBans.stream()
					.map { message ->
						messageChannel.createMessage(
							message
						)
					}
					.collect(MonoCollector.toMono())
			}
	}

	override val description = "Check a user's bans"
	override val name = "bans"
}
