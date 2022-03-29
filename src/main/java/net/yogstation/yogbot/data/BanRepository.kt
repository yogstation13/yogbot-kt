package net.yogstation.yogbot.data

import net.yogstation.yogbot.data.entity.Ban
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface BanRepository : CrudRepository<Ban, Long>, JpaSpecificationExecutor<Ban> {
	override fun findById(id: Long): Optional<Ban>
}
