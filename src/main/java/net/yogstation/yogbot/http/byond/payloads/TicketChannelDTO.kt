package net.yogstation.yogbot.http.byond.payloads

import com.fasterxml.jackson.annotation.JsonProperty

class TicketChannelDTO(
	@JsonProperty("key") val key: String,
	@JsonProperty("ticketid") val ticketId: String,
	@JsonProperty("message") val message: String,
	@JsonProperty("roundid") val roundId: String?,
	@JsonProperty("user") val user: String
)
