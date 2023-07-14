package net.yogstation.yogbot.data

import net.yogstation.yogbot.data.entity.StaffBan
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import java.util.*

interface StaffBanRepository : CrudRepository<StaffBan, Long>, JpaSpecificationExecutor<StaffBan> {
	override fun findById(id: Long): Optional<StaffBan>
}
