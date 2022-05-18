package net.yogstation.yogbot.http.byond

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import net.yogstation.yogbot.config.ByondConfig
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.http.ByondEndpoint
import net.yogstation.yogbot.http.byond.payloads.UnclaimedTicketDTO
import net.yogstation.yogbot.util.HttpUtil
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
        // Send the ticket to the admin channel
		val embed = EmbedCreateSpec.builder()
			.title("Ticket #${unclaimedTicketDTO.id} Unclaimed")
			.description("${unclaimedTicketDTO.ckey}: ${unclaimedTicketDTO.message}")
			.color(Color.RED)
			.build()
		return client.getGuildById(Snowflake.of(discordConfig.mainGuildID)).flatMap { guild ->
			guild.getChannelById(Snowflake.of(discordChannelsConfig.channelAdminBotspam)).flatMap { channel ->
				if(channel is TextChannel) channel.createMessage(embed)
				else Mono.empty<Any>()
			}
		}.then(HttpUtil.ok("Success"))
    }
}
