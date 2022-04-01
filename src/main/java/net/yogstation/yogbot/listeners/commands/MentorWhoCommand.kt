package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.ByondConnector
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.util.DiscordUtil
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Pulls a list of currently online mentors
 */
@Component
class MentorWhoCommand(
	discordConfig: DiscordConfig,
	private val byondConnector: ByondConnector,
) : TextCommand(discordConfig) {
	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		return byondConnector.requestAsync("?mentorwho").map { result ->
			var mentors= (if (result.hasError()) result.error else result.value) as String
			mentors = mentors.replace("\u0000".toRegex(), "")
			DiscordUtil.reply(event, mentors)
		}
	}

	override val name = "mentorwho"
	override val description = "Get current mentors online."
}
