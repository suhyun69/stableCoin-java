package com.stablecoin.payout.adapter.in.kafka;

import java.util.List;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.payout.application.RangeNotAvailableException;

/**
 * 소비자 처리 실패의 종류. DLT 메시지의 failure-kind 헤더 값이다.
 */
enum FailureKind {

	/** 일시 장애로 재시도했지만 끝내 실패함. 원인이 해결되면 그대로 다시 넣어도 된다. */
	RETRIES_EXHAUSTED,

	/** 해석할 수 없는 메시지. 다시 넣어도 같으므로 메시지를 고쳐야 한다. */
	PERMANENT,

	/** 예상하지 못한 오류. 안전하게 판단할 수 없어 사람이 확인해야 한다. */
	REQUIRES_REVIEW;

	static final String HEADER = "failure-kind";

	/** 재시도하는 일시 장애 */
	static final List<Class<? extends Exception>> RETRYABLE = List.of(ChainUnavailableException.class,
			TransientDataAccessException.class, DataAccessResourceFailureException.class,
			CannotCreateTransactionException.class, RangeNotAvailableException.class);

	/**
	 * 예외(감싼 예외 포함)로 실패 종류를 정한다.
	 */
	static FailureKind of(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = (cause.getCause() == cause) ? null : cause.getCause()) {
			if (cause instanceof InvalidKafkaMessageException) {
				return PERMANENT;
			}
			for (Class<? extends Exception> retryable : RETRYABLE) {
				if (retryable.isInstance(cause)) {
					return RETRIES_EXHAUSTED;
				}
			}
		}
		return REQUIRES_REVIEW;
	}

}
