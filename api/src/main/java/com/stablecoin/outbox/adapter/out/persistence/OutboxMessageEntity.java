package com.stablecoin.outbox.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_messages")
class OutboxMessageEntity {

	static final String PENDING = "PENDING";

	@Id
	private UUID id;

	@Column(name = "aggregate_type", nullable = false, length = 50)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(nullable = false, length = 100)
	private String type;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "available_at", nullable = false)
	private Instant availableAt;

	protected OutboxMessageEntity() {
	}

	OutboxMessageEntity(UUID id, String aggregateType, UUID aggregateId, String type, String payload) {
		this.id = id;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.type = type;
		this.payload = payload;
		this.status = PENDING;
		this.attempts = 0;
		this.availableAt = Instant.now();
	}

	String type() {
		return type;
	}

	String status() {
		return status;
	}

	String payload() {
		return payload;
	}

	UUID aggregateId() {
		return aggregateId;
	}

}
