package net.yogstation.yogbot.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
data class StickyRole(
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	val id: Long,
	@Column(nullable = false)
	val discordId: Long,
	@Column(nullable = false)
	var roleId: Long
) {
	constructor(discordId: Long, roleId: Long) : this(0, discordId, roleId)
}
