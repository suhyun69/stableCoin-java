package com.stablecoin.chain.adapter.in.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.chain.application.port.in.ChainQueryUseCase;
import com.stablecoin.web.ApiErrorCode;

class ChainHealthIndicatorTest {

	private final ChainQueryUseCase chainQuery = mock(ChainQueryUseCase.class);

	private final ChainHealthIndicator indicator = new ChainHealthIndicator(chainQuery);

	@Test
	void upWithChainIdWhenNodeResponds() {
		given(chainQuery.getChainId()).willReturn(31337L);

		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("chainId", 31337L);
	}

	@Test
	void downWhenNodeUnavailable() {
		given(chainQuery.getChainId())
			.willThrow(new ChainUnavailableException(new ResourceAccessException("Connection refused")));

		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("code", ApiErrorCode.CHAIN_UNAVAILABLE);
	}

	@Test
	void downWhenNodeReturnsUnexpectedResponse() {
		given(chainQuery.getChainId()).willThrow(new RestClientException("bad response"));

		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("code", ApiErrorCode.CHAIN_ERROR);
	}

}
