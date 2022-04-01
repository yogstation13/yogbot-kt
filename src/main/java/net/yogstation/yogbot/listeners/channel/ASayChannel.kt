package net.yogstation.yogbot.listeners.channel

import discord4j.common.util.Snowflake
import net.yogstation.yogbot.ByondConnector
import net.yogstation.yogbot.config.DiscordChannelsConfig
import org.springframework.stereotype.Component

@Component
class ASayChannel(channelsConfig: DiscordChannelsConfig, byondConnector: ByondConnector) : RelayChannel(
	channelsConfig, byondConnector
) {
	override val channel: Snowflake = Snowflake.of(channelsConfig.channelAsay)
	override val method: String = "asay"
	override val imagesAllowed = true
}
