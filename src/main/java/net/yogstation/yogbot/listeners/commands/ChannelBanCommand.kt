package net.yogstation.yogbot.listeners.commands

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.PartialMember
import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import reactor.core.publisher.Mono

/**
 * Base class for any command that gives and removes a channel ban such as !mentorban and !loreban
 * Not used for !staffban because of the warning system
 */
abstract class ChannelBanCommand(discordConfig: DiscordConfig, permissions: PermissionsManager) : PermissionsCommand(
	discordConfig, permissions
) {
	/**
	 * The role to give/remove
	 */
	protected abstract val banRole: Snowflake
	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		if (event.message.memberMentions.size != 1) return DiscordUtil.reply(
			event,
			"Usage is `${discordConfig.commandPrefix}$name [@UserName]`"
		)
		val partialMember: PartialMember = event.message.memberMentions[0]
		// If they have the rule, remove it
		return if (partialMember.roleIds.contains(banRole)) partialMember.removeRole(
			banRole,
			"Ban lifted by ${
				if (event.message
						.author
						.isPresent
				) event.message
					.author
					.get()
					.username else "unknown"
			}"
		).and(DiscordUtil.reply(event, "Ban lifted successfully"))
		// If they don't have the rule, give it
		else partialMember.addRole(
			banRole,
			"Ban applied by ${if (event.message.author.isPresent) event.message.author.get().username else "unknown"}"
		)
			.and(DiscordUtil.reply(event, "Ban applied successfully"))
	}
}
