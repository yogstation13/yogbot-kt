package net.yogstation.yogbot

import com.fasterxml.jackson.databind.ObjectMapper
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.presence.ClientActivity
import discord4j.core.`object`.presence.ClientPresence
import discord4j.gateway.intent.IntentSet
import discord4j.rest.RestClient
import net.yogstation.yogbot.config.DiscordConfig
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.*
import java.util.function.*

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
class Yogbot {
	@Bean
	fun getGatewayDiscordClient(config: DiscordConfig): GatewayDiscordClient? {
		val client = DiscordClientBuilder.create(config.botToken)
			.build()
			.gateway()
			.setInitialPresence { ClientPresence.online(ClientActivity.playing("I AM A GOD")) }
			.setEnabledIntents(IntentSet.all())
			.login()
			.block() ?: return null
		client.on(
			ReadyEvent::class.java,
			Function<ReadyEvent, Publisher<Any>> { event: ReadyEvent ->
				Mono.fromRunnable {
					val self: User = event.self
					logger.info("Logged in as {}", self.username)
				}
			}
		).subscribe()
		return client
	}

	@Bean
	fun getRestClient(client: GatewayDiscordClient): RestClient {
		return client.restClient
	}

	@Bean
	fun getRandom(): Random {
		return Random()
	}

	@Bean
	fun getWebClient(): WebClient {
		return WebClient.create()
	}

	@Bean
	fun getObjectMapper(): ObjectMapper {
		return ObjectMapper()
	}

	@Bean
	fun getRestTemplate(): RestTemplate {
		return RestTemplate()
	}

	private val logger = LoggerFactory.getLogger(Yogbot::class.java)

	companion object {
		/**
		 * Maximum number of characters in a message
		 */
		const val MAX_MESSAGE_LENGTH = 2000
	}
}

fun main(args: Array<String>) {
	@Suppress("SpreadOperator")
	SpringApplicationBuilder(Yogbot::class.java).build().run(*args)
}
