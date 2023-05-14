package net.yogstation.yogbot.listeners.channel

import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.reaction.ReactionEmoji
import net.yogstation.yogbot.config.DiscordChannelsConfig
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class MemesChannel(channelsConfig: DiscordChannelsConfig) : AbstractChannel(channelsConfig) {
	override val channel: Snowflake = Snowflake.of(channelsConfig.channelMemes)
	val memetypes = setOf("mp4", "mov", "webm", "gif", "jpg", "jpeg", "png")

	override fun handle(event: MessageCreateEvent): Mono<*> {
		val message = event.message
		// If the message has an attachment, an embed, or a type in memetypes
		return if (
			message.attachments.size > 0 ||
			message.embeds.size > 0 ||
			memetypes.contains(message.content.split(".").last()) ||
			message.content.contains("://tenor.com/", ignoreCase = true) ||
			message.content.contains("://imgur.com/", ignoreCase = true)
		) {
			// Java is strange with unicode in strings, this is thumbs up and down emoji
			message.addReaction(ReactionEmoji.unicode("\uD83D\uDC4D"))
				.and(message.addReaction(ReactionEmoji.unicode("\uD83D\uDC4E")))
		} else {
			message.delete("Non-meme in meme channel")
		}
	}
}
