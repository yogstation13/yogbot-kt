package net.yogstation.yogbot.listeners.channel

import discord4j.common.util.Snowflake
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.config.DiscordChannelsConfig
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class MemesChannel(channelsConfig: DiscordChannelsConfig) : AbstractChannel(channelsConfig) {
	override val channel: Snowflake = Snowflake.of(channelsConfig.channelMemes)

	override fun handle(event: MessageCreateEvent): Mono<*> {
		val message = event.message
		// If the message has an attachment, an embed, or a link to mp4/gif it must be a meme
		if (message.attachments.size > 0 ||
			message.embeds.size > 0 ||
			message.content.contains(".mp4") ||
			message.content.contains(".gif")
		) {
			// Java is strange with unicode in strings, this is thumbs up and down emoji
			return message.addReaction(ReactionEmoji.unicode("\uD83D\uDC4D"))
				.and(message.addReaction(ReactionEmoji.unicode("\uD83D\uDC4E")))
		}
		return Mono.empty<Any>()
	}
}
