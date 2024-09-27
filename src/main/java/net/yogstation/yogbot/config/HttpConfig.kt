package net.yogstation.yogbot.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "yogbot.http")
data class HttpConfig @ConstructorBinding constructor(
	// Public facing address of yogbot
	var publicPath: String

)
