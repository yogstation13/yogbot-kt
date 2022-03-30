package net.yogstation.yogbot.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "yogbot.database")
data class DatabaseConfig @ConstructorBinding constructor(
	// Connection Info, default matches the game server's default
	val hostname: String,
	val port: Int,
	val byondDatabase: String,
	val yogbotDatabase: String,
	val username: String,
	val password: String,

	// Database prefix
	val prefix: String
)
