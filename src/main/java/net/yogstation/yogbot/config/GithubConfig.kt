package net.yogstation.yogbot.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "yogbot.github")
data class GithubConfig @ConstructorBinding constructor(
	// Token for authenticating the webhook
	val hmac: String,

	// Oauth token
	val token: String,

	// API link to the base repo, allows for testing
	val repoLink: String,
)
