package com.stablecoin.payout.adapter.out.persistence;

import java.math.BigInteger;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutStatus;

@Entity
@Table(name = "payouts")
class PayoutEntity {

	@Id
	private UUID id;

	@Column(name = "merchant_id", nullable = false, length = 64)
	private String merchantId;

	@Column(name = "recipient_address", nullable = false, length = 42)
	private String recipientAddress;

	@Column(name = "amount_wei", nullable = false, precision = 78, scale = 0)
	private BigInteger amountWei;

	@Column(name = "idempotency_key", nullable = false, unique = true)
	private String idempotencyKey;

	@Column(name = "request_fingerprint", nullable = false, length = 64)
	private String requestFingerprint;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PayoutStatus status;

	protected PayoutEntity() {
	}

	PayoutEntity(Payout payout) {
		this.id = payout.id();
		this.merchantId = payout.merchantId();
		this.recipientAddress = payout.recipientAddress();
		this.amountWei = payout.amountWei();
		this.idempotencyKey = payout.idempotencyKey();
		this.requestFingerprint = payout.requestFingerprint();
		this.status = payout.status();
	}

	Payout toDomain() {
		return new Payout(id, merchantId, recipientAddress, amountWei, idempotencyKey, requestFingerprint, status);
	}

}
