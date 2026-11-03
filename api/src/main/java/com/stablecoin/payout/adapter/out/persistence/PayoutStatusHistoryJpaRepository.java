package com.stablecoin.payout.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PayoutStatusHistoryJpaRepository extends JpaRepository<PayoutStatusHistoryEntity, UUID> {

	List<PayoutStatusHistoryEntity> findByPayoutIdOrderByOccurredAt(UUID payoutId);

}
