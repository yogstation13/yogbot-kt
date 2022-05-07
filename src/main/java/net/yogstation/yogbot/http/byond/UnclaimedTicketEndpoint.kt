package net.yogstation.yogbot.http.byond

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.http.byond.payloads.UnclaimedTicketDTO
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@RestController
class UnclaimedTicketEndpoint(
		private val client: GatewayDiscordClient,
		private val discordConfig: DiscordConfig,
		private val discordChannelsConfig: DiscordChannelsConfig
) {
    // Function to take an unclaimed ticket DTO and send the ticket to the admin channel
    @PostMapping("/byond/unclaimedticket")
    fun unclaimedTicket(@RequestBody unclaimedTicketDTO: UnclaimedTicketDTO) {
        // Send the ticket to the admin channel
		val message: String = "@here Unclaimed Ticket #${unclaimedTicketDTO.id} (${unclaimedTicketDTO.ckey}): ${unclaimedTicketDTO.message}"
		client.getGuildById(Snowflake.of(discordConfig.mainGuildID)).flatMap { guild ->
			guild.getChannelById(Snowflake.of(discordChannelsConfig.channelAdmin)).flatMap { channel ->
				channel.restChannel.createMessage(message)
			}
		}.subscribe();
    }
}
