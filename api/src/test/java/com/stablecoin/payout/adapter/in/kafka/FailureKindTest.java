package com.stablecoin.payout.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.client.ResourceAccessException;

import com.stablecoin.chain.application.ChainUnavailableException;

class FailureKindTest {

	@Test
	void temporaryFailuresAreRetriesExhausted() {
		assertThat(FailureKind.of(new ChainUnavailableException(new ResourceAccessException("Connection refused"))))
			.isEqualTo(FailureKind.RETRIES_EXHAUSTED);
		assertThat(FailureKind.of(new CannotAcquireLockException("deadlock detected")))
			.isEqualTo(FailureKind.RETRIES_EXHAUSTED);
		assertThat(FailureKind.of(new DataAccessResourceFailureException("Connection refused")))
			.isEqualTo(FailureKind.RETRIES_EXHAUSTED);
		assertThat(FailureKind.of(new CannotCreateTransactionException("Could not open JPA EntityManager")))
			.isEqualTo(FailureKind.RETRIES_EXHAUSTED);
	}

	@Test
	void invalidMessageIsPermanent() {
		assertThat(FailureKind.of(new InvalidPayoutMessageException("invalid", new IllegalArgumentException())))
			.isEqualTo(FailureKind.PERMANENT);
	}

	@Test
	void anythingElseRequiresReview() {
		assertThat(FailureKind.of(new IllegalStateException("unexpected state")))
			.isEqualTo(FailureKind.REQUIRES_REVIEW);
	}

	@Test
	void looksThroughTheListenerWrapper() {
		assertThat(FailureKind.of(new ListenerExecutionFailedException("listener failed",
				new ChainUnavailableException(new ResourceAccessException("down")))))
			.isEqualTo(FailureKind.RETRIES_EXHAUSTED);
		assertThat(FailureKind.of(new ListenerExecutionFailedException("listener failed",
				new InvalidPayoutMessageException("invalid", null))))
			.isEqualTo(FailureKind.PERMANENT);
	}

}
