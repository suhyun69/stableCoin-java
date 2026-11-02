package com.stablecoin.chain.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import com.stablecoin.chain.application.port.out.ChainNodePort;

class ChainIdGuardTest {

	private final ChainNodePort chainNode = mock(ChainNodePort.class);

	private final ChainIdGuard guard = new ChainIdGuard(chainNode, 31337L);

	@Test
	void matchingChainIdPasses() {
		given(chainNode.getChainId()).willReturn(31337L);

		assertThatCode(guard::verify).doesNotThrowAnyException();
	}

	@Test
	void mismatchThrowsWithBothIds() {
		given(chainNode.getChainId()).willReturn(1L);

		assertThatThrownBy(guard::verify).isInstanceOf(ChainIdMismatchException.class)
			.hasMessageContaining("31337")
			.hasMessageContaining("1");
	}

	@Test
	void ensureVerifiedAsksTheNodeOnlyOnceAfterSuccess() {
		given(chainNode.getChainId()).willReturn(31337L);

		guard.ensureVerified();
		guard.ensureVerified();

		then(chainNode).should(times(1)).getChainId();
	}

	@Test
	void ensureVerifiedRetriesAfterNodeWasUnavailable() {
		given(chainNode.getChainId())
			.willThrow(new ChainUnavailableException(new ResourceAccessException("Connection refused")))
			.willReturn(31337L);

		assertThatThrownBy(guard::ensureVerified).isInstanceOf(ChainUnavailableException.class);
		guard.ensureVerified();

		then(chainNode).should(times(2)).getChainId();
	}

}
