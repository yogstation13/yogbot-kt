package net.yogstation.yogbot.listeners

import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.listeners.commands.TextCommand
import net.yogstation.yogbot.util.DiscordUtil
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.regex.*

@Component
class TextCommandListener(
	private val commands: List<TextCommand>,
	client: GatewayDiscordClient,
	private val config: DiscordConfig
) {
	private val commandPattern: Pattern = Pattern.compile("^!(?<command>\\w+)")

	init {
		client.on(MessageCreateEvent::class.java) { event: MessageCreateEvent -> handle(event) }.subscribe()
	}

	fun handle(event: MessageCreateEvent): Mono<*> {
		val matcher: Matcher = commandPattern.matcher(event.message.content)
		if (!matcher.find()) {
			return Mono.empty<Any>()
		}
		val commandName: String = matcher.group("command").lowercase()
		val command: TextCommand = commands.firstOrNull { command: TextCommand ->
			command.name == commandName
		} ?: return DiscordUtil.reply(event, "Command ${event.message.content.split(" ", limit = 2)[0]} not found")
		return command.handle(event)
	}
}
