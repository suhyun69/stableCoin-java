package com.stablecoin.payout.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.stablecoin.payout.domain.PayoutStatus;

@Entity
@Table(name = "payout_status_history")
class PayoutStatusHistoryEntity {

	@Id
	private UUID id;

	@Column(name = "payout_id", nullable = false)
	private UUID payoutId;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", length = 30)
	private PayoutStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, length = 30)
	private PayoutStatus toStatus;

	@Column(nullable = false, length = 100)
	private String reason;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected PayoutStatusHistoryEntity() {
	}

	PayoutStatusHistoryEntity(UUID payoutId, PayoutStatus fromStatus, PayoutStatus toStatus, String reason) {
		this.id = UUID.randomUUID();
		this.payoutId = payoutId;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.reason = reason;
		this.occurredAt = Instant.now();
	}

	PayoutStatus fromStatus() {
		return fromStatus;
	}

	PayoutStatus toStatus() {
		return toStatus;
	}

	String reason() {
		return reason;
	}

}
