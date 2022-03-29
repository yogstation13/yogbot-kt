package net.yogstation.yogbot.util

import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageCreateSpec
import reactor.core.publisher.Mono

object DiscordUtil {
	fun reply(event: MessageCreateEvent, message: String): Mono<*> {
		return event.message
			.channel
			.flatMap { channel ->
				channel.createMessage(
					MessageCreateSpec.builder().messageReference(event.message.id).content(message).build()
				)
			}
	}

	fun reply(event: MessageCreateEvent, embed: EmbedCreateSpec): Mono<*> {
		return event.message
			.channel
			.flatMap { channel ->
				channel.createMessage(
					MessageCreateSpec.builder()
						.messageReference(event.message.id)
						.content("")
						.addEmbed(embed)
						.build()
				)
			}
	}

	fun send(event: MessageCreateEvent, message: String?): Mono<*> {
		val channelMono: Mono<MessageChannel> = event.message.channel
		return channelMono.flatMap { channel: MessageChannel ->
			channel.createMessage(
				message
			)
		}
	}
}
