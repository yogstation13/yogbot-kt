package net.yogstation.yogbot.listeners.channel

import discord4j.common.util.Snowflake
import net.yogstation.yogbot.ByondConnector
import net.yogstation.yogbot.config.DiscordChannelsConfig

// @Component
class MSayChannel(channelsConfig: DiscordChannelsConfig, byondConnector: ByondConnector) : RelayChannel(
	channelsConfig, byondConnector
) {
	override val channel: Snowflake = Snowflake.of(channelsConfig.channelMsay)
	override val method: String = "msay"
}
