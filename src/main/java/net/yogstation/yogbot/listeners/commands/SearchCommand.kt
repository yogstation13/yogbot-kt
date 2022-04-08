package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.util.DiscordUtil
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class SearchCommand(
	discordConfig: DiscordConfig
) : TextCommand(discordConfig) {
	override val name = "search"

	override val description = "Searches the specified github for the string"

	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		val args: List<String> = event.message.content.split(" ", limit = 3)
		if(args.size < 3) return DiscordUtil.reply(event, "Usage: `${args[0]} <repo> <text>`")
		val repo = URLEncoder.encode(args[1], StandardCharsets.UTF_8)
		val query = URLEncoder.encode(args[2], StandardCharsets.UTF_8)
		return DiscordUtil.reply(event, "https://github.com/yogstation13/$repo/search?q=$query")
	}
}
