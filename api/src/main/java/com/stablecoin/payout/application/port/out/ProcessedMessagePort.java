package com.stablecoin.payout.application.port.out;

import java.util.UUID;

/**
 * 소비자 inbox(processed_messages) 출력 포트. 호출한 쪽의 DB 트랜잭션에 참여한다.
 */
public interface ProcessedMessagePort {

	/**
	 * 이 소비자가 이 메시지를 처리했다고 기록한다. 이미 기록돼 있으면 아무것도 하지 않는다.
	 * @return 새로 기록했으면 true (처음 받은 메시지)
	 */
	boolean markProcessed(String consumerName, String messageId, UUID payoutId);

}
