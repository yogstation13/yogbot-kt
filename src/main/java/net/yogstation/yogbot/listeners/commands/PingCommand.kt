package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.ByondConnector
import net.yogstation.yogbot.config.ByondConfig
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.util.DiscordUtil
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class PingCommand(
	discordConfig: DiscordConfig,
	private val byondConnector: ByondConnector,
	private val byondConfig: ByondConfig
) : TextCommand(
	discordConfig
) {
	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		return byondConnector.requestAsync("?ping").flatMap { pingResponse ->
			if (pingResponse.hasError()) DiscordUtil.reply(event, pingResponse.error ?: "Unknown Error")
			else {
				val playerCount: Int = (pingResponse.value as Float).toInt()
				DiscordUtil.reply(
					event,
					"There are **$playerCount** players online, join them now with ${byondConfig.serverJoinAddress}"
				)
			}
		}
	}

	override val description = "Pings the server."
	override val name = "ping"
}
