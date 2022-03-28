package net.yogstation.yogbot.listeners.commands

import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.ByondConnector
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.util.DiscordUtil
import reactor.core.publisher.Mono

/**
 * Pulls a list of currently online admins
 */
class AdminWhoCommand(
	discordConfig: DiscordConfig, private val byondConnector: ByondConnector,
	private val channelsConfig: DiscordChannelsConfig
) : TextCommand(discordConfig) {
	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		return getAdmins(event.message.channelId, byondConnector, channelsConfig).flatMap {
			DiscordUtil.reply(event, it)
		}
	}

	override val name = "adminwho"
	override val description = "Get current admins online."

	companion object {
		fun getAdmins(
			channelID: Snowflake,
			byondConnector: ByondConnector,
			channelsConfig: DiscordChannelsConfig
		): Mono<String> {
			var byondMessage = "?adminwho"
			if (channelsConfig.isAdminChannel(channelID.asLong())) byondMessage += "&adminchannel=1"
			return byondConnector.requestAsync(byondMessage).map { result ->
				var admins = (if (result.hasError()) result.error else result.value) as String
				admins = admins.replace("\u0000".toRegex(), "")
				admins
			}
		}
	}
}
