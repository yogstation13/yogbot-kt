package net.yogstation.yogbot.http.byond.payloads

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class StatusDTO(
	@JsonProperty("key") val key: String,
	@JsonProperty("status") val status: String,
	@JsonProperty("map_name") val mapName: String?,
	@JsonProperty("round") val round: Int?,
	@JsonProperty("revision") val revision: String?
)
