package net.yogstation.yogbot.http.byond.payloads.tickets

import com.fasterxml.jackson.annotation.JsonProperty

class TicketRefreshDTO (
	@JsonProperty("key") var key: String,
	@JsonProperty("round_id") val roundId: Int,
	@JsonProperty("tickets") val tickets: Array<TicketData>
)

class TicketData (
	@JsonProperty("id") val id: Int,
	@JsonProperty("title") val title: String,
	@JsonProperty("ckey") val ckey: String,
	@JsonProperty("admin") val admin: String,
	@JsonProperty("resolved") val resolved: Int,
	@JsonProperty("interactions") val interactions: Array<String>
)
