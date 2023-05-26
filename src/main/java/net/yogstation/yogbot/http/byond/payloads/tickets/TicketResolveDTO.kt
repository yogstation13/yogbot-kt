package net.yogstation.yogbot.http.byond.payloads.tickets

import com.fasterxml.jackson.annotation.JsonProperty

class TicketResolveDTO (
	@JsonProperty("key") val key: String,
	@JsonProperty("resolved") val resolved: Int,
	@JsonProperty("id") val ticketId: Int
)
