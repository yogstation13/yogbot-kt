package net.yogstation.yogbot.listeners.channel

import discord4j.common.util.Snowflake
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.config.DiscordChannelsConfig
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class PublicChannel(channelsConfig: DiscordChannelsConfig) : AbstractChannel(channelsConfig) {
	override val channel: Snowflake = Snowflake.of(channelsConfig.channelPublic)
	override fun handle(event: MessageCreateEvent): Mono<*> {
		val message = event.message
		if (!message.contains("hohoho", ignoreCase = true) && !message.contains("hohoho", ignoreCase = true)) {
			message.delete("You must be festive in #public! Include "hohoho" or "ho ho ho" in your message for the christmas season!")
		}
	}
}
