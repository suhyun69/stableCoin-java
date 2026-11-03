package com.stablecoin.payout.application;

/**
 * 이미 쓴 멱등성 키로 내용이 다른 지급을 요청했을 때 발생한다.
 */
public class IdempotencyKeyConflictException extends RuntimeException {

	public IdempotencyKeyConflictException(String idempotencyKey) {
		super("Idempotency-Key '" + idempotencyKey + "' was already used with a different request");
	}

}
