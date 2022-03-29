package net.yogstation.yogbot.data

import net.yogstation.yogbot.data.entity.Ban
import net.yogstation.yogbot.data.entity.StickyRole
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import java.sql.Date
import java.time.LocalDateTime
import java.util.*

interface StickyRoleRepository: CrudRepository<StickyRole, Long> {
	fun findAllByDiscordId(discordId: Long): List<StickyRole>
}
