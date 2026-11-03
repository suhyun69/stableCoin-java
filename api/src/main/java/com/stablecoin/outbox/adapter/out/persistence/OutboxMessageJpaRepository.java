package com.stablecoin.outbox.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxMessageJpaRepository extends JpaRepository<OutboxMessageEntity, UUID> {

	List<OutboxMessageEntity> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);

}
