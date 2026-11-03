package com.stablecoin.outbox.domain;

import java.util.UUID;

/**
 * 발행기가 잡은(임대한) outbox 메시지. payloadJson은 저장된 JSON 그대로이고, attempts는 이번 시도를 포함한 횟수다.
 */
public record ClaimedOutboxMessage(UUID id, String aggregateType, UUID aggregateId, String type, String payloadJson,
		int attempts) {
}
