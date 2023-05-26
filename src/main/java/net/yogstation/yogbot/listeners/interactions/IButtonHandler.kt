package net.yogstation.yogbot.listeners.interactions

import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import reactor.core.publisher.Mono

interface IButtonHandler {
	val prefix: String

	fun handle(event: ButtonInteractionEvent): Mono<*>
}
