package net.yogstation.yogbot.data.entity

import java.util.*
import javax.persistence.*

@Entity
data class StickyRole (
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	val id: Long,
	@Column(nullable = false)
	val discordId: Long,
	@Column(nullable = false)
	var roleId: Long
) {
	constructor(discordId: Long, roleId: Long): this(0, discordId, roleId)
}
