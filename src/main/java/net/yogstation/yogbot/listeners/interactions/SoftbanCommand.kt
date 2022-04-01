package net.yogstation.yogbot.listeners.interactions

import discord4j.common.util.Snowflake
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.MessageComponent
import discord4j.core.`object`.component.TextInput
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent
import discord4j.core.event.domain.interaction.UserInteractionEvent
import discord4j.discordjson.json.ComponentData
import net.yogstation.yogbot.bans.BanManager
import net.yogstation.yogbot.permissions.PermissionsManager
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SoftbanCommand(private val permissions: PermissionsManager, private val banManager: BanManager) :
	IUserCommand,
	IModalSubmitHandler {

	override val name: String
		get() = "Softban"

	override fun handle(event: UserInteractionEvent): Mono<*> {
		return openModal(event, event.targetId)
	}

	fun openModal(
		event: ApplicationCommandInteractionEvent,
		userid: Snowflake
	): Mono<*> = if (!permissions.hasPermission(event.interaction.member.orElse(null), "ban")) event.reply()
		.withEphemeral(true).withContent("You do not have permission to run that command") else event.presentModal()
		.withCustomId("$idPrefix-${userid.asString()}")
		.withTitle("Softban Menu")
		.withComponents(
			ActionRow.of(TextInput.small("duration", "Ban Duration (Minutes)").required(false)),
			ActionRow.of(TextInput.paragraph("reason", "Ban Reason"))
		)

	override val idPrefix: String
		get() = "softban"

	override fun handle(event: ModalSubmitInteractionEvent): Mono<*> {
		val toBan: Snowflake = Snowflake.of(event.customId.split("-").toTypedArray()[1])
		val reasonDuration = getReasonDuration(event)
		if(reasonDuration.error != null || reasonDuration.reason == null || reasonDuration.duration == null)
			return event.reply().withEphemeral(true).withContent(reasonDuration.error ?: "Unknown Error")

		return banManager.ban(
			toBan, reasonDuration.reason!!, reasonDuration.duration!!,
			event.interaction.user.username
		).flatMap { result ->
			if (result.error != null || result.value == null) event.reply().withEphemeral(true)
				.withContent(result.error ?: "Unknown Error")
			else result.value.and(event.reply().withEphemeral(true).withContent("Ban issued successfully"))
		}
	}

	private fun getReasonDuration(event: ModalSubmitInteractionEvent): ReasonDuration {
		val reasonDuration = ReasonDuration()
		for (component in event.components) {
			if (component.type != MessageComponent.Type.ACTION_ROW) continue
			if (component.data.components().isAbsent) continue
			for (data in component.data.components().get()) {
				parseData(data, reasonDuration)
			}
		}
		return reasonDuration
	}

	private fun parseData(
		data: ComponentData,
		reasonDuration: ReasonDuration
	) {
		if (data.customId().isAbsent) return
		if ("reason" == data.customId().get()) {
			if (data.value().isAbsent) reasonDuration.error = "Please specify a kick reason"
			reasonDuration.reason = data.value().get()
		} else if ("duration" == data.customId().get()) {
			if (data.value().isAbsent || data.value().get() == "") reasonDuration.duration = 0
			else reasonDuration.duration = data.value().get().toInt()
		}
	}

	private class ReasonDuration {
		var duration: Int? = null
		var reason: String? = null
		var error: String? = null
	}
}
