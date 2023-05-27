package net.yogstation.yogbot

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.interaction.ButtonInteractionEvent
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent
import discord4j.core.`object`.Embed
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.Button
import discord4j.core.`object`.component.TextInput
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.InteractionPresentModalSpec
import discord4j.core.spec.MessageCreateSpec
import discord4j.core.spec.MessageEditSpec
import discord4j.rest.util.Color
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.http.byond.payloads.tickets.TicketRefreshDTO
import net.yogstation.yogbot.listeners.interactions.IButtonHandler
import net.yogstation.yogbot.listeners.interactions.IModalSubmitHandler
import net.yogstation.yogbot.util.ByondLinkUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.net.URLEncoder

@Component
class ByondTicketManager (
	private val client: GatewayDiscordClient,
	private val channelsConfig: DiscordChannelsConfig,
	private val byondConnector: ByondConnector,
	private val databaseManager: DatabaseManager
): IButtonHandler, IModalSubmitHandler {
	private var tickets: MutableMap<Int, ByondTicket> = HashMap()
	override val prefix: String = "ticket-"
	override val idPrefix: String = "ticket-"

	private val logger = LoggerFactory.getLogger(this.javaClass)
	private var currentRound = 0

	init {
		logger.info("Requesting ticket data")
		byondConnector.requestAsync("?ticket_status").map {
			if(it.hasError()) logger.error(it.error!!)
			logger.info(it.value.toString())
		}.subscribe()
	}

	override fun handle(event: ButtonInteractionEvent): Mono<*> {
		val args = event.customId.split("-")
		val id: Int
		try {
			id = args[1].toInt()
		} catch (e: NumberFormatException) {
			logger.error("${args[1]} is not a valid integer")
			return event.reply("Error parsing button ID").withEphemeral(true)
		}

		val ticket = tickets[id] ?: return event.reply("Ticket not found").withEphemeral(true)

		return when (args[2]) {
			"administer" -> ticketAct("ticket_administer", ticket, event)
			"reply" -> reply(event)
			"resolve" -> ticketAct("ticket_resolve", ticket, event)
			"showAll" -> ticket.showAll(event)
			else -> {
				logger.error("${args[2]} is not a valid button action")
				return event.reply("Error processing button action.").withEphemeral(true)
			}
		}
	}

	private fun ticketAct(ticketAction: String, ticket: ByondTicket, event: ButtonInteractionEvent): Mono<*> {
		val result = ByondLinkUtil.getCkey(event.interaction.user.id, databaseManager)
		if(result.hasError()) return event.reply(result.error!!).withEphemeral(true)
		return byondConnector.requestAsync("?$ticketAction&id=${ticket.id}&ckey=${result.value}").flatMap {
			if(it.hasError()) event.reply(it.error!!).withEphemeral(true)
			else if(it.value !is String) {
				logger.error("Administer ticket topic returned ${it.value} (${it.value!!.javaClass}), expected string")
				event.reply("Byond returned non string value, check logs for details").withEphemeral(true)
			} else if(it.value.startsWith("ERROR:")) {
				event.reply(it.value).withEphemeral(true)
			} else {
				event.deferEdit()
			}
		}
	}

	private fun reply(event: ButtonInteractionEvent): Mono<*> {
		return event.presentModal(InteractionPresentModalSpec.builder()
			.title("Ticket Reply Modal")
			.customId(event.customId)
			.addComponent(ActionRow.of(
				TextInput.paragraph("message", "Message:").required()
			)).build())
	}

	override fun handle(event: ModalSubmitInteractionEvent): Mono<*> {
		val args = event.customId.split("-")
		val id: Int
		try {
			id = args[1].toInt()
		} catch (e: NumberFormatException) {
			logger.error("${args[1]} is not a valid integer")
			return event.reply("Error parsing button ID").withEphemeral(true)
		}

		val ticket = tickets[id] ?: return event.reply("Ticket not found").withEphemeral(true)

		val result = ByondLinkUtil.getCkey(event.interaction.user.id, databaseManager)
		if(result.hasError()) return event.reply(result.error!!).withEphemeral(true)

		val message = event.getComponents(TextInput::class.java)
			.asSequence()
			.filter { it.customId == "message" }
			.map { it.value }
			.filter { it.isPresent }
			.map { it.get() }
			.first()

		if(message.isEmpty()) return event.reply("Message cannot be empty").withEphemeral(true)

		val encoded = URLEncoder.encode(message, Charsets.UTF_8)

		return byondConnector.requestAsync("?ticket_reply&id=${ticket.id}&ckey=${result.value}&message=$encoded").flatMap {
			if(it.hasError()) event.reply(it.error!!).withEphemeral(true)
			else if(it.value !is String) {
				logger.error("Administer ticket topic returned ${it.value} (${it.value!!.javaClass}), expected string")
				event.reply("Byond returned non string value, check logs for details").withEphemeral(true)
			} else {
				if(it.value.startsWith("ERROR:")) event.reply(it.value).withEphemeral(true)
				else event.deferEdit()
			}
		}
	}

	fun newTicket(ckey: String, message: String, id: Int, round: Int): Mono<String> {
		val setRound = setRound(round)
		val ticket = ByondTicket(ckey, message, id)
		tickets[id] = ticket
		return setRound.then(ticket.createMessage(client, channelsConfig.channelLiveTickets))
	}

	fun administerTicket(ckey: String, id: Int): Mono<String> {
		val ticket = tickets[id] ?: return Mono.just("Unknown ticket ID")
		ticket.admin = ckey
		return ticket.updateMessage(client, channelsConfig.channelLiveTickets)
	}

	fun addInteraction(ckey: String, message: String, id: Int): Mono<String> {
		val ticket = tickets[id] ?: return Mono.just("Unknown ticket ID")
		ticket.interactions.add("$ckey: $message")
		return ticket.updateMessage(client, channelsConfig.channelLiveTickets)
	}

	fun resolveTicket(id: Int, resolved: Boolean): Mono<String> {
		val ticket = tickets[id] ?: return Mono.just("Unknown ticket ID")
		ticket.resolved = resolved
		return ticket.updateMessage(client, channelsConfig.channelLiveTickets)
	}

	fun setRound(roundID: Int, force: Boolean = false): Mono<*> {
		if(currentRound == roundID && !force) return Mono.empty<Void>()
		currentRound = roundID
		tickets.clear()
		return client.getChannelById(Snowflake.of(channelsConfig.channelLiveTickets)).flatMap { channel ->
			channel.restChannel.getMessagesAfter(Snowflake.of(0)).flatMap {
				channel.restChannel.getRestMessage(Snowflake.of(it.id())).delete("Ticket from previous round")
			}.collectList()
		}
	}

	fun refreshTickets(payload: TicketRefreshDTO): Mono<String> {
		logger.info("Refreshing tickets")
		val setRound = setRound(payload.roundId, true)
		var ticketsMono: Mono<String> = Mono.just("")
		payload.tickets.forEach { data ->
			val byondTicket = ByondTicket(
				data.ckey,
				data.title,
				data.id,
				admin = data.admin,
				resolved = data.resolved == 1,
				interactions = data.interactions.toMutableList()
			)
			tickets[data.id] = byondTicket
			val ticketMono = byondTicket.createMessage(client, channelsConfig.channelLiveTickets)
			ticketsMono = ticketsMono.flatMap<String> { prevString ->
				ticketMono.map<String> { newString ->
					if(prevString.isEmpty() != newString.isEmpty()) "$prevString$newString" // One is empty, one isn't, concatenate
					else if(prevString.isEmpty()) "" // Both are empty
					else "$prevString\n$newString" // Both are populated
				}
			}
		}
		return setRound.then(ticketsMono)
	}
}

data class ByondTicket(
	var ckey: String,
	var title: String,
	var id: Int,
	var message: Snowflake = Snowflake.of(0),
	var admin: String = "Unclaimed",
	var resolved: Boolean = false,
	var interactions: MutableList<String> = ArrayList(),
	var isTrimmed: Boolean = false
) {
	private val logger = LoggerFactory.getLogger(this.javaClass)

	private fun toEmbed(): EmbedCreateSpec {
		val builder = EmbedCreateSpec.builder()

		builder.title("#$id: $title")
		builder.addField("Admin", admin, true)
		builder.color(if(resolved) Color.GRAY else if(admin == "Unclaimed") Color.RED else Color.GREEN)

		val description = StringBuilder("```\n")
		val suffix = "\n```"
		for(interaction in interactions.reversed()) {
			if (description.length + suffix.length + interaction.length >= Embed.MAX_DESCRIPTION_LENGTH) {
				isTrimmed = true
				break
			}
			description.append("\n")
			description.append(interaction.reversed())
		}
		description.append(suffix)
		builder.description(description.toString().reversed())

		return builder.build()
	}

	private fun getActionRow(): ActionRow {
		val buttons = mutableListOf<Button>(
			if(admin == "Unclaimed") Button.primary("ticket-$id-administer", "Administer")
			else Button.secondary("ticket-$id-administer", "Administer"),
			Button.primary("ticket-$id-reply", "Reply"),
			Button.primary("ticket-$id-resolve", "Resolve")
		)
		if(isTrimmed) buttons.add(Button.secondary("ticket-$id-showAll", "View Full Ticket"))
		return ActionRow.of(buttons)
	}

	@Volatile
	private var updating = true
	@Volatile
	private var isDirty = false
	fun updateMessage(client: GatewayDiscordClient, channelId: Long): Mono<String> {
		synchronized(this) {
			if (updating) {
				isDirty = true
				return Mono.just("")
			}
			updating = true
		}
		return doMessageUpdate(client, channelId).doOnCancel {
			updating = false // Only should be hit if the connection dies
		}
	}

	private fun doMessageUpdate(client: GatewayDiscordClient, channelId: Long): Mono<String> {
		return client.getMessageById(Snowflake.of(channelId), message)
			.doOnError {
				logger.error("Error getting channel, stopping updates")
				updating = false // No retry because errored
			}
			.onErrorComplete()
			.flatMap { message ->
				message.edit(MessageEditSpec
					.builder()
					.embeds(listOf(toEmbed()))
					.components(listOf(getActionRow()))
					.build()
				).flatMap {
					postMessageUpdate(client, channelId)
				}
			}
	}

	private fun postMessageUpdate(client: GatewayDiscordClient, channelId: Long): Mono<String> {
		synchronized(this) {
			if(!isDirty) {
				updating = false
				return Mono.just("")
			}
			isDirty = false
		}
		return doMessageUpdate(client, channelId)
	}

	fun createMessage(client: GatewayDiscordClient, channelId: Long): Mono<String> {
		return client.getChannelById(Snowflake.of(channelId)).flatMap { channel ->
			if(channel !is TextChannel) return@flatMap Mono.just("Live tickets channel is not a text channel")

			val messageBuilder = MessageCreateSpec.builder()
			messageBuilder.addEmbed(toEmbed())
			messageBuilder.addComponent(getActionRow())

			channel.createMessage(messageBuilder.build()).flatMap {
				message = it.id
				postMessageUpdate(client, channelId)
			}
		}
	}

	fun showAll(event: ButtonInteractionEvent): Mono<*> {
		val message = StringBuilder("Ticket $id:\n```\n")
		val suffix = "\n```"
		val monos: MutableList<Mono<*>> = ArrayList()
		var hasReplied = false
		interactions.forEach {
			if(message.length + suffix.length + it.length >= Message.MAX_CONTENT_LENGTH) {
				message.append(suffix)
				if(!hasReplied) {
					monos.add(event.reply(message.toString()).withEphemeral(true))
					hasReplied = true
				}
				else monos.add(event.createFollowup(message.toString()).withEphemeral(true))
				message.setLength(0)
				message.append("```\n")
			}
			message.append(it).append("\n")
		}
		message.append(suffix)
		if(!hasReplied) monos.add(event.reply(message.toString()).withEphemeral(true))
		else monos.add(event.createFollowup(message.toString()).withEphemeral(true))
		return Mono.`when`(monos)
	}
}
