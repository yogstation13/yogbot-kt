package net.yogstation.yogbot.listeners.interactions.slash

import discord4j.common.util.Snowflake
import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.command.ApplicationCommandInteractionOptionValue
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import net.yogstation.yogbot.listeners.interactions.ISlashCommand
import net.yogstation.yogbot.permissions.PermissionsManager
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import net.yogstation.yogbot.listeners.interactions.UnbanCommand as UserUnbanCommand

@Component
class UnbanCommand(
	private val unbanCommand: UserUnbanCommand,
	private val permissions: PermissionsManager
): ISlashCommand {
	override val name = "ban"

	override fun handle(event: ChatInputInteractionEvent): Mono<*> {
		val userid: Snowflake = event.getOption("person").flatMap(ApplicationCommandInteractionOption::getValue)
			.map(ApplicationCommandInteractionOptionValue::asSnowflake).orElse(Snowflake.of(0))
		if(userid.asLong() == 0L) event.reply().withEphemeral(true).withContent("Failed to find person")

		return unbanCommand.openModal(event, userid)
	}

	override val uri: String
		get() = "slash_unban.json"
}
