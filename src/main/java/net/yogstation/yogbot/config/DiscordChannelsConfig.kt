package net.yogstation.yogbot.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "yogbot.channels")
class DiscordChannelsConfig @ConstructorBinding constructor (
	val channelImportantAdmin: Long,
	val channelAdmin: Long,
	val channelAdmemes: Long,
	val channelAdminBotspam: Long,
	val channelAsay: Long,
	val channelMsay: Long,
	val channelMentor: Long,
	val channelStaffPublic: Long,
	val channelCouncil: Long,
	val channelDevelopmentPublic: Long,
	val channelSpriter: Long,
	val channelMaintainerChat: Long,
	val channelPublic: Long,
	val channelOOC: Long,
	val channelMemes: Long,
	val channelBotspam: Long,
	val channelPublicLog: Long,
	val channelMapping: Long,
	val channelBanAppeals: Long,
	val channelPlayerComplaints: Long,
	val channelAdminComplaints: Long,
	val channelStaffApplications: Long,
	val channelMentorApplications: Long,
	val channelGithubSpam: Long,
	val channelBugReports: Long,
) {

	private val adminChannels: Set<Long> by lazy {
		setOf(
			channelAdmemes,
			channelAdmin,
			channelCouncil,
			channelAdminBotspam
		)
	}

	/**
	 * Returns if the given long points to an admin channel
	 * @param channelID The value to check
	 */
	fun isAdminChannel(channelID: Long): Boolean {
		return adminChannels.contains(channelID)
	}
}
