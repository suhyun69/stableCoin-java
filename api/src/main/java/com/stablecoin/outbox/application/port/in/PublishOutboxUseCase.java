package com.stablecoin.outbox.application.port.in;

/**
 * outbox 발행 입력 포트. 한 번 호출에 메시지를 한 묶음 잡아서 발행한다.
 */
public interface PublishOutboxUseCase {

	PublishResult publishBatch();

	/**
	 * @param claimed 잡은 메시지 수
	 * @param published 발행하고 PUBLISHED로 바꾼 수
	 * @param failed 발행에 실패한 수 (재시도 예약 또는 FAILED)
	 */
	record PublishResult(int claimed, int published, int failed) {
	}

}
