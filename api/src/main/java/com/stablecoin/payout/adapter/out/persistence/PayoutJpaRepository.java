package com.stablecoin.payout.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PayoutJpaRepository extends JpaRepository<PayoutEntity, UUID> {

	List<PayoutEntity> findByIdempotencyKey(String idempotencyKey);

}
