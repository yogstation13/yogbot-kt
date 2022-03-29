package net.yogstation.yogbot.listeners

import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.listeners.commands.TextCommand
import net.yogstation.yogbot.util.DiscordUtil
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class TextCommandListener(
	private val commands: List<TextCommand>,
	client: GatewayDiscordClient,
	private val config: DiscordConfig
) {
	init {
		client.on(MessageCreateEvent::class.java) { event: MessageCreateEvent -> handle(event) }.subscribe()
	}

	fun handle(event: MessageCreateEvent): Mono<*> {
		if (!event.message.content.startsWith(config.commandPrefix)) return Mono.empty<Any>()
		val command: TextCommand = commands.firstOrNull { command: TextCommand ->
			event.message.content.startsWith(
				config.commandPrefix + command.name
			)
		} ?: return DiscordUtil.reply(event, "Command ${event.message.content.split(" ", limit = 2)[0]} not found")
		return command.handle(event)
	}
}
