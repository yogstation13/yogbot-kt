package net.yogstation.yogbot.listeners.commands.image

import net.yogstation.yogbot.config.DiscordConfig
import org.springframework.stereotype.Component
import java.util.Random

@Component
class CouncilCommand(discordConfig: DiscordConfig, random: Random) : ImageCommand(discordConfig, random) {

	override val images: List<String>
		get() = listOf(
			"https://cdn.discordapp.com/attachments/134720091576205312/871910842164183070/8PQmYdL.png",
			"https://cdn.discordapp.com/attachments/734475284446707753/826240137225830400/image0.png", // Ashcorr gets fooled by Morderhel
			"https://cdn.discordapp.com/attachments/734475284446707753/804697809323556864/unknown.png", // Get a sense of humor
			"https://cdn.discordapp.com/attachments/734475284446707753/804192496322084864/ban.png", // Banned by public vote
			"https://cdn.discordapp.com/attachments/734475284446707753/800864882870059028/image0.png", // Council don't know hotkeys
			"https://cdn.discordapp.com/attachments/734475284446707753/959203191478681661/unknown.png", // Xantam moderator app
			"https://cdn.discordapp.com/attachments/734475284446707753/1004911936124764303/Screenshot_2022-08-04_183955.png", // You expect adam to read?
			"https://cdn.discordapp.com/attachments/734475284446707753/1028837256481493012/unknown.png", // YOU'RE COUNCIL. SO ARE YOU
			"https://cdn.discordapp.com/attachments/734475284446707753/1031688697533435904/IMG_9748.png", // Adam has thoughts?
			"https://cdn.discordapp.com/attachments/608072786224742455/1035978792230989844/unknown.png" // Cope
		)
	override val title = "Council Image"
	override val name = "council"
	override val description = "Pictures of the goings-on at the top of the hierarchy"
}
