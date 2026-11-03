package com.stablecoin.outbox.adapter.out.persistence;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Component;

import com.stablecoin.outbox.application.port.out.OutboxPort;
import com.stablecoin.outbox.domain.OutboxMessage;

/**
 * outbox_messages 테이블에 PENDING 상태로 저장한다. payload는 JSON(jsonb)으로 바꿔 저장한다.
 */
@Component
class OutboxPersistenceAdapter implements OutboxPort {

	private final OutboxMessageJpaRepository repository;

	private final JsonMapper jsonMapper;

	OutboxPersistenceAdapter(OutboxMessageJpaRepository repository, JsonMapper jsonMapper) {
		this.repository = repository;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void enqueue(OutboxMessage message) {
		repository.save(new OutboxMessageEntity(message.id(), message.aggregateType(), message.aggregateId(),
				message.type(), jsonMapper.writeValueAsString(message.payload())));
	}

}
