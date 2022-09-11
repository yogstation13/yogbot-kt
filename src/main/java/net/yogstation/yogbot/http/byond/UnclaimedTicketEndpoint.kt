package net.yogstation.yogbot.http.byond

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import net.yogstation.yogbot.config.ByondConfig
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.http.ByondEndpoint
import net.yogstation.yogbot.http.byond.payloads.UnclaimedTicketDTO
import net.yogstation.yogbot.util.HttpUtil
import org.apache.commons.text.StringEscapeUtils
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class UnclaimedTicketEndpoint(
		private val client: GatewayDiscordClient,
		private val discordConfig: DiscordConfig,
		private val discordChannelsConfig: DiscordChannelsConfig,
		byondConfig: ByondConfig
): ByondEndpoint(byondConfig) {
    // Function to take an unclaimed ticket DTO and send the ticket to the admin channel
    @PostMapping("/byond/ticket_unclaimed")
    fun unclaimedTicket(@RequestBody unclaimedTicketDTO: UnclaimedTicketDTO): Mono<HttpEntity<String>> {
		val keyError = validateKey(unclaimedTicketDTO.key)
		if (keyError != null)
			return keyError
		val ticketContent = StringEscapeUtils.unescapeHtml4(unclaimedTicketDTO.message)
        // Send the ticket to the admin channel
		val message =
				"Unclaimed Ticket ${unclaimedTicketDTO.round}#${unclaimedTicketDTO.id} (${unclaimedTicketDTO.ckey}): $ticketContent"
		return client.getGuildById(Snowflake.of(discordConfig.mainGuildID)).flatMap { guild ->
			guild.getChannelById(Snowflake.of(discordChannelsConfig.channelAdmin)).flatMap { channel ->
				channel.restChannel.createMessage(message)
			}
		}.then(HttpUtil.ok("Success"))
    }
}
