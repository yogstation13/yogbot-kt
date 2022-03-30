package net.yogstation.yogbot.data

import net.yogstation.yogbot.data.entity.StickyRole
import org.springframework.data.repository.CrudRepository

interface StickyRoleRepository : CrudRepository<StickyRole, Long> {
	fun findAllByDiscordId(discordId: Long): List<StickyRole>
}
