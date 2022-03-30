package net.yogstation.yogbot.http.byond.payloads

import com.fasterxml.jackson.annotation.JsonProperty

class CkeyMessageDTO(
	@JsonProperty("key") val key: String,
	@JsonProperty("ckey") val ckey: String,
	@JsonProperty("message") val message: String
)
