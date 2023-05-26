package net.yogstation.yogbot.http.byond.payloads.tickets

import com.fasterxml.jackson.annotation.JsonProperty

class TicketAdministerDTO (
	@JsonProperty("key") val key: String,
	@JsonProperty("ckey") val ckey: String,
	@JsonProperty("id") val ticketId: Int
)
