package net.yogstation.yogbot.http.byond

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.`object`.presence.ClientActivity
import discord4j.core.`object`.presence.ClientPresence
import discord4j.core.spec.EmbedCreateSpec
import discord4j.discordjson.json.MessageCreateRequest
import discord4j.rest.util.Color
import net.yogstation.yogbot.config.ByondConfig
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.http.ByondEndpoint
import net.yogstation.yogbot.http.byond.payloads.StatusDTO
import net.yogstation.yogbot.util.HttpUtil
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Gets the server status, specifically at pre-round in the lobby, where all subscribers are pinged,
 * then when the round starts and ends, updating the bot's activity tagline
 */
@RestController
class ServerStatusEndpoint(
	byondConfig: ByondConfig,
	private val channelsConfig: DiscordChannelsConfig,
	private val client: GatewayDiscordClient,
	private val discordConfig: DiscordConfig
) :
	ByondEndpoint(byondConfig) {

	@PostMapping("/byond/roundstatus")
	fun receiveData(@RequestBody payload: StatusDTO): Mono<HttpEntity<String>> {
		val keyError = validateKey(payload.key)
		if (keyError != null) return keyError

		return when(payload.status) {
			"lobby" -> {
				val embed = EmbedCreateSpec.builder()
				embed.author("New round notifier", "", "https://i.imgur.com/GPZgtbe.png")
				embed.description(
					"A new round is about to begin! Join now at ${byondConfig.serverJoinAddress}"
				)
				embed.addField("Map Name", payload.mapName ?: "Unknown", true)
				embed.addField("Revision", payload.revision ?: "Unknown", true)
				embed.addField("Round Number", payload.round?.toString() ?: "0", true)
				embed.addField("Changelog", "No Changes", true)
				embed.color(Color.of(0x62F442))

				client.getChannelById(Snowflake.of(channelsConfig.channelBotspam)).flatMap { channel: Channel ->
					channel.restChannel.createMessage(
						MessageCreateRequest.builder().content("<@&${discordConfig.subscriberRole}>")
							.embed(embed.build().asRequest()).build()
					)
				}
					.and(client.updatePresence(ClientPresence.online(ClientActivity.playing("Round Starting!"))))
					.then(HttpUtil.ok("Status set"))
			}

			"ingame" -> client.updatePresence(ClientPresence.online(ClientActivity.playing("In Game")))
				.then(HttpUtil.ok("Status set"))

			else -> client.updatePresence(ClientPresence.online(ClientActivity.playing("Round Ending")))
				.then(HttpUtil.ok("Status set"))
		}
	}
}
