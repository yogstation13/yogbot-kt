package net.yogstation.yogbot.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "yogbot.discord")
data class DiscordConfig @ConstructorBinding constructor(

	val botToken: String,
	val commandPrefix: String,

	val serverName: String,

	val oauthAuthorizeUrl: String,

	val oauthTokenUrl: String,
	val oauthUserInfoUrl: String,
	val oauthClientId: String,
	val oauthClientSecret: String,

	val msayWebhookUrl: String,
	val asayWebhookUrl: String,
	val ticketWebhookUrl: String,

	val mainGuildID: Long,
	val aoRole: Long,
	val mentorRole: Long,
	val jesterRole: Long,
	val subscriberRole: Long,

	val manualVerifyRole: Long,
	val byondVerificationRole: Long,

	val donorRole: Long,

	val staffRole: Long,

	// Punishment Roles
	val softBanRole: Long,
	val loreBanRole: Long,
	val mentorBanRole: Long,
	val wikibanRole: Long,

	val firstWarningRole: Long,
	val secondWarningRole: Long,
	val staffPublicBanRole: Long,

	val cannotAnnounceRole: Long,
	val staffCouncilBanRole: Long,
	val cmBanRole: Long,
	val townhallBanRole: Long,
	val devBanRole: Long,
	val mutedRole: Long,
	val cantReactInStaffPublicRole: Long,
	val purgedRole: Long,
	val unemojiworthyRole: Long,
	val reactionlessRole: Long,
	val quickfireBanRole: Long
) {

	// Softbans are not handled via sticky roles
	private val stickyRoles: Set<Long> by lazy {
		setOf(
			cannotAnnounceRole,
			staffPublicBanRole,
			staffCouncilBanRole,
			cmBanRole,
			townhallBanRole,
			loreBanRole,
			devBanRole,
			mentorBanRole,
			firstWarningRole,
			secondWarningRole,
			mutedRole,
			cantReactInStaffPublicRole,
			purgedRole,
			unemojiworthyRole,
			reactionlessRole,
			wikibanRole,
			quickfireBanRole
		)
	}

	/**
	 * Checks if the given role is a sticky role
	 */
	fun isStickyRole(roleID: Long): Boolean {
		return stickyRoles.contains(roleID)
	}
}
