package net.yogstation.yogbot.listeners.interactions.slash

import discord4j.common.util.Snowflake
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.command.ApplicationCommandInteractionOptionValue
import net.yogstation.yogbot.listeners.interactions.ISlashCommand
import net.yogstation.yogbot.permissions.PermissionsManager
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import net.yogstation.yogbot.listeners.interactions.StaffBanCommand as SBCommand

@Component
class StaffBanSlashCommand(
	private val sbCommand: SBCommand,
	private val permissions: PermissionsManager
): ISlashCommand {
	override val name = "staffban"

	override fun handle(event: ChatInputInteractionEvent): Mono<*> {
		val userid: Snowflake = event.getOption("person").flatMap(ApplicationCommandInteractionOption::getValue)
			.map(ApplicationCommandInteractionOptionValue::asSnowflake).orElse(Snowflake.of(0))
		if(userid.asLong() == 0L) event.reply().withEphemeral(true).withContent("Failed to find person")

		return if (!permissions.hasPermission(event.interaction.member.orElse(null), "staffban")) event.reply()
			.withEphemeral(true).withContent("You do not have permission to run that command")
		else event.interaction.guild.flatMap { guild ->
			guild.getMemberById(userid).flatMap { member ->
				sbCommand.staffBan(member, event)
			}
		}
	}

	override val uri: String
		get() = "slash_staffban.json"
}
