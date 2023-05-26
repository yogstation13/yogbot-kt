package net.yogstation.yogbot.http.byond

import net.yogstation.yogbot.ByondTicketManager
import net.yogstation.yogbot.config.ByondConfig
import net.yogstation.yogbot.http.ByondEndpoint
import net.yogstation.yogbot.http.byond.payloads.tickets.TicketAdministerDTO
import net.yogstation.yogbot.http.byond.payloads.tickets.TicketInteractionDTO
import net.yogstation.yogbot.http.byond.payloads.tickets.TicketNewDTO
import net.yogstation.yogbot.http.byond.payloads.tickets.TicketRefreshDTO
import net.yogstation.yogbot.http.byond.payloads.tickets.TicketResolveDTO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class TicketEndpoints (
	byondConfig: ByondConfig,
	private val ticketManager: ByondTicketManager
): ByondEndpoint(byondConfig) {
	private val logger = LoggerFactory.getLogger(this.javaClass)

	private fun handleError(errorMsg: Mono<String>): Mono<HttpEntity<String>> {
		return errorMsg.map {
			if(it.isEmpty()) ResponseEntity.status(HttpStatus.OK).body("Success")
			else {
				logger.error(it)
				ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(it)
			}
		}
	}

	@PostMapping("/byond/ticket_new")
	fun newTicket(@RequestBody ticketPayload: TicketNewDTO): Mono<HttpEntity<String>> {
		val keyError = validateKey(ticketPayload.key)
		if(keyError != null) return keyError
		return handleError(ticketManager.newTicket(
			ticketPayload.ckey,
			ticketPayload.message,
			ticketPayload.ticketId,
			ticketPayload.round
		))
	}

	@PostMapping("/byond/ticket_administer")
	fun administerTicket(@RequestBody ticketPayload: TicketAdministerDTO): Mono<HttpEntity<String>> {
		val keyError = validateKey(ticketPayload.key)
		if(keyError != null) return keyError
		return handleError(ticketManager.administerTicket(ticketPayload.ckey, ticketPayload.ticketId))
	}

	@PostMapping("/byond/ticket_interaction")
	fun addInteraction(@RequestBody ticketPayload: TicketInteractionDTO): Mono<HttpEntity<String>> {
		val keyError = validateKey(ticketPayload.key)
		if(keyError != null) return keyError
		return handleError(ticketManager.addInteraction(ticketPayload.ckey, ticketPayload.message, ticketPayload.ticketId))
	}

	@PostMapping("/byond/ticket_resolve")
	fun resolveTicket(@RequestBody ticketPayload: TicketResolveDTO): Mono<HttpEntity<String>> {
		val keyError = validateKey(ticketPayload.key)
		if(keyError != null) return keyError
		return handleError(ticketManager.resolveTicket(ticketPayload.ticketId, ticketPayload.resolved == 1))
	}

	@PostMapping("/byond/ticket_refresh")
	fun refreshTickets(@RequestBody payload: TicketRefreshDTO): Mono<HttpEntity<String>> {
		val keyError = validateKey(payload.key)
		if(keyError != null) return keyError
		payload.key = ""
		return handleError(ticketManager.refreshTickets(payload))
	}
}
