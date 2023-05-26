package net.yogstation.yogbot.http.byond.payloads.tickets

import com.fasterxml.jackson.annotation.JsonProperty

class TicketNewDTO (
	@JsonProperty("key") val key: String,
	@JsonProperty("ckey") val ckey: String,
	@JsonProperty("message") val message: String,
	@JsonProperty("id") val ticketId: Int,
	@JsonProperty("round") val round: Int
)
