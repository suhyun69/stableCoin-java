package com.stablecoin.outbox.application.port.out;

import com.stablecoin.outbox.domain.ClaimedOutboxMessage;

/**
 * outbox 메시지를 메시지 브로커로 보내는 출력 포트. 브로커가 받았다고 확인할 때까지 기다린 뒤 돌아온다.
 * 같은 메시지를 두 번 보낼 수 있으므로(at-least-once) 받는 쪽은 메시지 ID로 중복을 걸러야 한다.
 */
public interface OutboxEventPublisherPort {

	/**
	 * @throws RuntimeException 브로커가 받지 못한 경우
	 */
	void publish(ClaimedOutboxMessage message);

}
