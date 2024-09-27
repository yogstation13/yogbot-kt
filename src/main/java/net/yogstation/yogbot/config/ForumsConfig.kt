package net.yogstation.yogbot.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "yogbot.forums")
data class ForumsConfig @ConstructorBinding constructor(
	val xenforoKey: String,
	val banAppealsForum: Int,
	val playerComplaintsForum: Int,
	val adminComplaintsForums: Int,
	val adminAdminComplaintsForum: Int,
	val staffApplicationsForum: Int,
	val mentorApplicationsForum: Int,

	val activityGroups: String,
	val activityExempt: String
)
