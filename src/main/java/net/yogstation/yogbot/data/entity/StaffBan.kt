package net.yogstation.yogbot.data.entity

import org.springframework.data.jpa.domain.Specification
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Temporal
import javax.persistence.TemporalType

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
		this(0, discordId, Date(System.currentTimeMillis()), Date(System.currentTimeMillis() + banLength), null)

	companion object {
		private const val banLength: Long = 3 * 30 * 24 * 60 * 60 * 1000L // 3 Months, in millis
		fun isBanFor(discordId: Long): Specification<StaffBan> {
			return Specification { root, query, criteriaBuilder ->
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
