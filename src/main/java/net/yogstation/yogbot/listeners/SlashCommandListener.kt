package net.yogstation.yogbot.listeners

import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.core.event.domain.interaction.UserInteractionEvent
import net.yogstation.yogbot.listeners.interactions.ISlashCommand
import net.yogstation.yogbot.listeners.interactions.IUserCommand
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class SlashCommandListener(private val commands: List<ISlashCommand>, client: GatewayDiscordClient) {
	init {
		client.on(ChatInputInteractionEvent::class.java) { event: ChatInputInteractionEvent -> handle(event) }.subscribe()
	}

	fun handle(event: ChatInputInteractionEvent): Mono<*> {
		return Flux.fromIterable(commands)
			.filter { command: ISlashCommand -> command.name == event.commandName }
			.next()
			.flatMap { command: ISlashCommand -> command.handle(event) }
	}
}
