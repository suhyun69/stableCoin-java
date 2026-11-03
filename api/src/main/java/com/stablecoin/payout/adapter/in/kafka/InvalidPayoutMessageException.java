package com.stablecoin.payout.adapter.in.kafka;

/**
 * 메시지를 해석할 수 없을 때 발생한다 (JSON이 깨짐, payoutId 없음 등). 다시 받아도 결과가 같으므로 재시도하지 않고 DLT로 보낸다.
 */
class InvalidPayoutMessageException extends RuntimeException {

	InvalidPayoutMessageException(String message, Throwable cause) {
		super(message, cause);
	}

}
