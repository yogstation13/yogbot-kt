package net.yogstation.yogbot.data

import net.yogstation.yogbot.data.entity.Ban
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import java.sql.Date
import java.time.LocalDateTime
import java.util.*

interface BanRepository: CrudRepository<Ban, Long>, JpaSpecificationExecutor<Ban> {
	override fun findById(id: Long): Optional<Ban>
}
