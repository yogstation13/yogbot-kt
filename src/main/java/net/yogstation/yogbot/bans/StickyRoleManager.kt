package net.yogstation.yogbot.bans

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.data.StickyRoleRepository
import net.yogstation.yogbot.data.entity.StickyRole
import net.yogstation.yogbot.util.LogChannel
import net.yogstation.yogbot.util.MonoCollector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Records any sticky bans a user logs out with, reapplies them on join
 */
@Component
class StickyRoleManager(
	private val discordConfig: DiscordConfig,
	private val logChannel: LogChannel,
	private val stickyRoleRepository: StickyRoleRepository
) {
	private val logger: Logger = LoggerFactory.getLogger(javaClass)

	/**
	 * Handles a member logging out by saving their sticky roles to the database
	 * @param member The member to check
	 */
	fun doLogout(member: Member?): Mono<*> {
		if (member == null) return logChannel.log("Unable to check for sticky roles")
		return Mono.fromCallable {
			stickyRoleRepository.saveAll(member.roleIds.filter { discordConfig.isStickyRole(it.asLong()) }
				.map { StickyRole(member.id.asLong(), it.asLong()) }).count()
		}.subscribeOn(Schedulers.boundedElastic()).flatMap { count ->
			if (count > 0) {
				member.privateChannel.flatMap { channel -> channel.createMessage("You have left the ${discordConfig.serverName} server with $count sticky role${if (count != 1) "s" else ""}. They will be reapplied when you rejoin.") }
					.and(logChannel.log("**${member.tag}** left the server with $count sticky role${if (count != 1) "s" else ""}"))
			} else {
				Mono.empty<Any>()
			}
		}
	}

	/**
	 * Checks if a user logged off with sticky bans, if they did, reapplies them
	 * @param member The member to check
	 */
	fun doLogin(member: Member): Mono<*> {
		return Mono.fromCallable {
			val roles: List<StickyRole> = stickyRoleRepository.findAllByDiscordId(member.id.asLong()).filter { discordConfig.isStickyRole(it.roleId) }
			stickyRoleRepository.deleteAll(roles)
			roles
		}.subscribeOn(Schedulers.boundedElastic()).flatMap { roles ->
			var stickyRoleCount = 0
			var resultMono = roles.map {
				stickyRoleCount++
				member.addRole(Snowflake.of(it.roleId), "Sticky role")
			}.stream().collect(MonoCollector.toMono())
			if (stickyRoleCount > 0) {
				resultMono =
					resultMono.and(logChannel.log("**${member.tag}** rejoined the server with $stickyRoleCount sticky role${if (stickyRoleCount != 1) "s" else ""}"))
			}
			resultMono
		}
	}
}
