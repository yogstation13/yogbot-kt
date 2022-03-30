package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.ByondConnector
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class ToggleOOCCommand(discordConfig: DiscordConfig, private val byondConnector: ByondConnector,
					   permissions: PermissionsManager
) : PermissionsCommand (
	discordConfig, permissions
) {
	override val requiredPermissions = "toggleooc"

	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		return byondConnector.requestAsync("?toggleooc").flatMap { result ->
			if (result.hasError()) DiscordUtil.reply(
				event,
				result.error ?: "Unknown Error"
			) else DiscordUtil.reply(
				event,
				"OOC has been ${
					if ((result.value as Float).toInt() == 1) "enabled" else "disabled"
				}"
			)
		}
	}

	override val description = "Toggles server OOC."
	override val name = "toggleooc"
}
