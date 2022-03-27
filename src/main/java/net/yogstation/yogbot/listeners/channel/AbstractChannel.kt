package net.yogstation.yogbot.listeners.channel

import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.config.DiscordChannelsConfig
import reactor.core.publisher.Mono

abstract class AbstractChannel(protected val channelsConfig: DiscordChannelsConfig) {
	abstract val channel: Snowflake
	abstract fun handle(event: MessageCreateEvent): Mono<*>
}