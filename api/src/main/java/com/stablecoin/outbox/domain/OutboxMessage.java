package com.stablecoin.outbox.domain;

import java.util.Map;
import java.util.UUID;

/**
 * 나중에 처리할 일. 대상(aggregate)과 같은 DB 트랜잭션으로 저장하므로, 대상이 저장되면 메시지도 반드시 남는다.
 * 같은 대상에 같은 type의 메시지는 하나만 저장된다.
 */
public record OutboxMessage(UUID id, String aggregateType, UUID aggregateId, String type,
		Map<String, Object> payload) {

	public static OutboxMessage of(String aggregateType, UUID aggregateId, String type, Map<String, Object> payload) {
		return new OutboxMessage(UUID.randomUUID(), aggregateType, aggregateId, type, Map.copyOf(payload));
	}

}
