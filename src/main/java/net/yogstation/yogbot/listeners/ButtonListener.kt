package net.yogstation.yogbot.listeners

import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import net.yogstation.yogbot.listeners.interactions.IButtonHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class ButtonListener(private val buttonHandlers: List<IButtonHandler>, client: GatewayDiscordClient) {
	private val logger = LoggerFactory.getLogger(this.javaClass)

	init {
		client.on(ButtonInteractionEvent::class.java) { event: ButtonInteractionEvent -> handle(event) }
			.subscribe()
	}

	fun handle(event: ButtonInteractionEvent): Mono<*> {
		return Flux.fromIterable(buttonHandlers)
			.filter { buttonHandler: IButtonHandler ->
				event.customId.startsWith(
					buttonHandler.prefix
				)
			}
			.next()
			.flatMap { command: IButtonHandler -> command.handle(event) }
			.doOnError {
				logger.error("Unexpected error handling button", it)
				event.reply("And unexpected error has occurred, please check the logs for details.").withEphemeral(true)
			}
	}
}
