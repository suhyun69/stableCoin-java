package com.stablecoin.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.stablecoin.web.ApiErrorCode;

/**
 * 노드 연결 상태. /actuator/health/chain 으로 조회하며, 연결할 수 없으면 DOWN(503)이다.
 */
@Component("chain")
public class ChainHealthIndicator implements HealthIndicator {

	private static final Logger log = LoggerFactory.getLogger(ChainHealthIndicator.class);

	private final ChainService chainService;

	public ChainHealthIndicator(ChainService chainService) {
		this.chainService = chainService;
	}

	@Override
	public Health health() {
		try {
			return Health.up().withDetail("chainId", chainService.getChainId()).build();
		}
		catch (ChainUnavailableException ex) {
			log.debug("Chain health check failed", ex);
			return Health.down().withDetail("code", ApiErrorCode.CHAIN_UNAVAILABLE).build();
		}
		catch (RuntimeException ex) {
			log.warn("Chain health check failed", ex);
			return Health.down().withDetail("code", ApiErrorCode.CHAIN_ERROR).build();
		}
	}

}
