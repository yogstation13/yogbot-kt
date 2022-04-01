package net.yogstation.yogbot.listeners.interactions.slash

import discord4j.common.util.Snowflake
import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.command.ApplicationCommandInteractionOptionValue
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import net.yogstation.yogbot.listeners.interactions.ISlashCommand
import net.yogstation.yogbot.listeners.interactions.KickCommand
import net.yogstation.yogbot.permissions.PermissionsManager
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class KickSlashCommand(
	private val kickCommand: KickCommand,
	private val permissions: PermissionsManager
): ISlashCommand {
	override val name = "kick"

	override fun handle(event: ChatInputInteractionEvent): Mono<*> {
		val userid: Snowflake = event.getOption("person").flatMap(ApplicationCommandInteractionOption::getValue)
			.map(ApplicationCommandInteractionOptionValue::asSnowflake).orElse(Snowflake.of(0))
		if(userid.asLong() == 0L) event.reply().withEphemeral(true).withContent("Failed to find person")

		return event.interaction.guild.flatMap { guild ->
			guild.getMemberById(userid).flatMap { member ->
				if (permissions.hasPermission(member, "kick")) event.reply().withEphemeral(true)
					.withContent("Cannot kick staff")
				else kickCommand.presentModal(event, userid)
			}
		}
	}

	override val uri: String
		get() = "slash_kick.json"
}
