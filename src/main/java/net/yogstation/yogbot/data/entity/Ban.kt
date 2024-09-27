package net.yogstation.yogbot.data.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import org.springframework.data.jpa.domain.Specification
import java.util.Date

@Entity
data class Ban(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "BIGINT(20) AUTO_INCREMENT")
	val id: Long,
	@Column(nullable = false)
	val discordId: Long,
	@Lob
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
	constructor(discordId: Long, reason: String, expiresAt: Date?) :
		this(0, discordId, reason, Date(System.currentTimeMillis()), expiresAt, null)

	companion object {
		fun isBanFor(discordId: Long): Specification<Ban> {
			return Specification { root, query, criteriaBuilder ->
				criteriaBuilder.equal(root.get(Ban_.discordId), discordId)
			}
		}

		fun isBanActive(): Specification<Ban> {
			return Specification { root, query, criteriaBuilder ->
				val now = Date(System.currentTimeMillis())
				val notExpire = criteriaBuilder.or(
					criteriaBuilder.greaterThan(root.get(Ban_.expiresAt), now),
					criteriaBuilder.isNull(root.get(Ban_.expiresAt))
				)
				criteriaBuilder.and(notExpire, criteriaBuilder.isNull(root.get(Ban_.revokedAt)))
			}
		}
	}
}
