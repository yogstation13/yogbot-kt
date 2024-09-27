package net.yogstation.yogbot.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "yogbot.byond")
data class ByondConfig @ConstructorBinding constructor(
	// The comms key for communicating to byond
	var serverKey: String,

	// The webhook key used by byond to communicate to yogbot
	var serverWebhookKey: String,

	// The address of the byond server
	var serverAddress: String,

	// The port of the byond server
	var serverPort: Int,

	// Public facing addressed, send to user to indicate where to join
	var serverJoinAddress: String
)
