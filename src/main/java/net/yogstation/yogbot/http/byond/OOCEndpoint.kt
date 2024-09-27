package net.yogstation.yogbot.http.byond

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import net.yogstation.yogbot.config.ByondConfig
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.http.byond.payloads.CkeyMessageDTO
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Relays ingame ooc messages to the ooc channel
 */
@RestController
class OOCEndpoint(
	client: GatewayDiscordClient,
	channelsConfig: DiscordChannelsConfig,
	byondConfig: ByondConfig
) : MessageRelayEndpoint(client, byondConfig) {
	override val channelId: Snowflake = Snowflake.of(channelsConfig.channelOOC)

	@PostMapping("/byond/oocmessage")
	fun handleMhelp(@RequestBody payload: CkeyMessageDTO): Mono<HttpEntity<String>> {
		val keyError = validateKey(payload.key)
		if (keyError != null) return keyError
		return receiveData(payload)
	}
}
