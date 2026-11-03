package com.stablecoin.outbox.application.port.out;

import com.stablecoin.outbox.domain.OutboxMessage;

/**
 * outbox 저장 출력 포트. 호출한 쪽의 DB 트랜잭션에 참여해야 한다 (따로 커밋하지 않는다).
 */
public interface OutboxPort {

	void enqueue(OutboxMessage message);

}
