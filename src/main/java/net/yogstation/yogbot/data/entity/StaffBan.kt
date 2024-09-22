package net.yogstation.yogbot.data.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Column
import jakarta.persistence.Temporal
import jakarta.persistence.TemporalType
import org.springframework.data.jpa.domain.Specification
import java.util.*

@Entity
data class StaffBan(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "BIGINT(20) AUTO_INCREMENT")
	val id: Long,
	@Column(nullable = false)
	val discordId: Long,
	@Temporal(TemporalType.TIMESTAMP)
	@Column(columnDefinition = "DATETIME NOT NULL DEFAULT NOW()")
	val issuedAt: Date,
	@Temporal(TemporalType.TIMESTAMP)
	var expiresAt: Date?,
	@Temporal(TemporalType.TIMESTAMP)
	var revokedAt: Date?,
) {

	constructor(discordId: Long) :
		this(0, discordId, Date(System.currentTimeMillis()), Date(System.currentTimeMillis() + BAN_LENGTH), null)

	companion object {
		private const val BAN_LENGTH: Long = 3 * 30 * 24 * 60 * 60 * 1000L // 3 Months, in millis
		fun isBanFor(discordId: Long): Specification<StaffBan> {
			return Specification { root, _, criteriaBuilder ->
				criteriaBuilder.equal(root.get(StaffBan_.discordId), discordId)
			}
		}

		fun isBanActive(): Specification<StaffBan> {
			return Specification { root, query, criteriaBuilder ->
				val now = Date(System.currentTimeMillis())
				val notExpire = criteriaBuilder.or(
					criteriaBuilder.greaterThan(root.get(StaffBan_.expiresAt), now),
					criteriaBuilder.isNull(root.get(StaffBan_.expiresAt))
				)
				criteriaBuilder.and(notExpire, criteriaBuilder.isNull(root.get(StaffBan_.revokedAt)))
			}
		}
	}
}
