package com.stablecoin.payout.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PayoutJpaRepository extends JpaRepository<PayoutEntity, UUID> {

	Optional<PayoutEntity> findByIdempotencyKey(String idempotencyKey);

}
