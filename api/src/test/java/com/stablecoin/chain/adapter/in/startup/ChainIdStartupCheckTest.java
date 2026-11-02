package com.stablecoin.chain.adapter.in.startup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.web.client.ResourceAccessException;

import com.stablecoin.chain.application.ChainIdGuard;
import com.stablecoin.chain.application.ChainIdMismatchException;
import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.chain.application.port.out.ChainNodePort;

class ChainIdStartupCheckTest {

	private final ChainNodePort chainNode = mock(ChainNodePort.class);

	private final ChainIdStartupCheck check = new ChainIdStartupCheck(new ChainIdGuard(chainNode, 31337L));

	@Test
	void mismatchStopsStartup() {
		given(chainNode.getChainId()).willReturn(1L);

		assertThatThrownBy(() -> check.run(new DefaultApplicationArguments()))
			.isInstanceOf(ChainIdMismatchException.class);
	}

	@Test
	void unavailableNodeDoesNotStopStartup() {
		given(chainNode.getChainId())
			.willThrow(new ChainUnavailableException(new ResourceAccessException("Connection refused")));

		assertThatCode(() -> check.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
	}

	@Test
	void matchingChainIdPasses() {
		given(chainNode.getChainId()).willReturn(31337L);

		assertThatCode(() -> check.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
	}

}
