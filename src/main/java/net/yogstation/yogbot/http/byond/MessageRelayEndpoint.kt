package net.yogstation.yogbot.http.byond

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import net.yogstation.yogbot.config.ByondConfig
import net.yogstation.yogbot.http.ByondEndpoint
import net.yogstation.yogbot.http.byond.payloads.CkeyMessageDTO
import net.yogstation.yogbot.util.HttpUtil
import org.apache.commons.text.StringEscapeUtils
import org.springframework.http.HttpEntity
import reactor.core.publisher.Mono

/**
 * Represents an endpoint that relays messages from byond to a channel without the use of a webhook
 */
abstract class MessageRelayEndpoint protected constructor(val client: GatewayDiscordClient, byondConfig: ByondConfig) :
	ByondEndpoint(
		byondConfig
	) {
	abstract val channelId: Snowflake
	fun receiveData(payload: CkeyMessageDTO): Mono<HttpEntity<String>> {
		return client.getChannelById(channelId)
			.flatMap { channel ->
				val message = StringEscapeUtils.unescapeHtml4(payload.message).replace("@", "@ ")

				channel.restChannel.createMessage("**${payload.ckey}**: $message")
					.then(HttpUtil.ok("Message sent"))
			}
	}
}
