package net.yogstation.yogbot.data.entity

import org.springframework.data.jpa.domain.Specification
import java.util.Date
import javax.persistence.*

@Entity
data class Ban (
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	val id: Long,
	@Column(nullable = false)
	val discordId: Long,
	@Column(nullable = false)
	var reason: String,
	@Temporal(TemporalType.TIMESTAMP)
	@Column(columnDefinition = "DATETIME NOT NULL DEFAULT NOW()")
	val issuedAt: Date,
	@Temporal(TemporalType.TIMESTAMP)
	var expiresAt: Date?,
	@Temporal(TemporalType.TIMESTAMP)
	var revokedAt: Date?,
) {
	constructor(discordId: Long, reason: String, expiresAt: Date?): this(0, discordId, reason, Date(System.currentTimeMillis()), expiresAt, null)

	companion object {
		fun isBanFor(discordId: Long): Specification<Ban> {
			return Specification { root, query, criteriaBuilder ->
				criteriaBuilder.equal(root.get(Ban_.discordId), discordId)
			}
		}

		fun isBanActive(): Specification<Ban> {
			return Specification { root, query, criteriaBuilder ->
				val now = Date(System.currentTimeMillis())
				val notExpire = criteriaBuilder.or(criteriaBuilder.greaterThan(root.get(Ban_.expiresAt), now), criteriaBuilder.isNull(root.get(Ban_.expiresAt)))
				criteriaBuilder.and(notExpire, criteriaBuilder.isNull(root.get(Ban_.revokedAt)))
			}
		}
	}
}
