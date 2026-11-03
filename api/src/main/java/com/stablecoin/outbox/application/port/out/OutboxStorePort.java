package com.stablecoin.outbox.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.stablecoin.outbox.domain.ClaimedOutboxMessage;

/**
 * 발행기가 쓰는 outbox 저장소 출력 포트. 각 메서드는 자기 트랜잭션으로 바로 커밋한다.
 */
public interface OutboxStorePort {

	/**
	 * 발행할 차례인 PENDING 메시지를 최대 limit개 잡는다. 다른 워커가 잡고 있는 메시지는 건너뛰고(SKIP LOCKED),
	 * 잡은 메시지는 lease 동안 다른 워커가 잡지 못한다. 잡을 때 attempts가 1 늘어난다.
	 */
	List<ClaimedOutboxMessage> claimBatch(String workerId, int limit, Duration lease);

	/**
	 * PUBLISHED로 바꾸고 잠금을 푼다.
	 * @return 아직 이 워커가 잡고 있던 메시지라 바꿨으면 true. 임대가 끝나 다른 워커가 가져갔으면 false
	 */
	boolean markPublished(UUID messageId, String workerId);

	/**
	 * 발행 실패를 기록하고 잠금을 푼다. retryAfter 뒤에 다시 잡을 수 있게 하거나, giveUp이면 FAILED로 바꾼다.
	 */
	void markFailed(UUID messageId, String workerId, String error, Duration retryAfter, boolean giveUp);

}
