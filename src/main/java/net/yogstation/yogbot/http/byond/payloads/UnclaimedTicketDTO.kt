package net.yogstation.yogbot.http.byond.payloads

import com.fasterxml.jackson.annotation.JsonProperty

class UnclaimedTicketDTO (
	@JsonProperty("ckey") val ckey: String,
	@JsonProperty("message") val message: String,
	@JsonProperty("id") val id: Int,
	@JsonProperty("key") val key: String,
	@JsonProperty("round") val round: String
)
