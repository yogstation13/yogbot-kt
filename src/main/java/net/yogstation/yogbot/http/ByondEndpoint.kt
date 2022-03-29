package net.yogstation.yogbot.http

import net.yogstation.yogbot.config.ByondConfig
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono

open class ByondEndpoint(protected val byondConfig: ByondConfig) {

	fun validateKey(key: String): Mono<HttpEntity<String>>? {
		if (key != byondConfig.serverWebhookKey) return Mono.just(
			ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid key")
		)
		return null
	}

}
