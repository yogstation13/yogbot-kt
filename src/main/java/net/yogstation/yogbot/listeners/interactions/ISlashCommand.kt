package net.yogstation.yogbot.listeners.interactions

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import net.yogstation.yogbot.listeners.IEventHandler

interface ISlashCommand : IInteractionHandler, IEventHandler<ChatInputInteractionEvent> {
	override val uri: String
		get() = "${name.lowercase()}.json"
}
