package net.yogstation.yogbot.listeners.channel

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.ByondConnector
import net.yogstation.yogbot.config.DiscordChannelsConfig
import org.apache.commons.text.StringEscapeUtils
import reactor.core.publisher.Mono
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * This is the parent type for all channels that simply take messages from discord and send them to byond
 * such as asay, msay, and ooc. This class takes any message sent in the channel and forwards it to the specified
 * byond endpoint, attaching images if set to do so
 */
abstract class RelayChannel(channelsConfig: DiscordChannelsConfig, private val byondConnector: ByondConnector) :
	AbstractChannel(
		channelsConfig
	) {

	/**
	 * Setting this to true will take attached images and include them in the message sent to byond
	 */
	open val imagesAllowed = false

	/**
	 * The method on the byond endpoint to process the messages
	 */
	abstract val method: String

	override fun handle(event: MessageCreateEvent): Mono<*> {
		if (event.message.author.isEmpty) return Mono.empty<Any>()
		val messageBuilder = StringBuilder(
			StringEscapeUtils.escapeHtml4(event.message.content)
		)
		// If images are allowed (asay only, mentors can cope) link to them
		if (imagesAllowed) {
			event.message.attachments.forEach { attachment ->
				logger.info("Attachment {} found", attachment.filename)
				if (attachment.filename.endsWith(".jpg") || attachment.filename.endsWith(".png")) {
					messageBuilder.append("<br><img src=\"").append(attachment.url).append("\" alt=\"Image\">")
				}
			}
		}

		val user: String = if (event.member.isPresent) {
			event.member.get().displayName
		} else {
			event.message.author.get().username
		}
		return byondConnector.requestAsync(
			"?$method=${URLEncoder.encode(messageBuilder.toString(), StandardCharsets.UTF_8)}&admin=${
			URLEncoder.encode(
				user,
				StandardCharsets.UTF_8
			)
			}"
		)
	}
}
