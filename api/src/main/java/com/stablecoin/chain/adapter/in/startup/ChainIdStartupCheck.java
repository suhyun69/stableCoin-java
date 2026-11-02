package com.stablecoin.chain.adapter.in.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.stablecoin.chain.application.ChainIdGuard;
import com.stablecoin.chain.application.ChainIdMismatchException;
import com.stablecoin.chain.application.ChainUnavailableException;

/**
 * 앱 시작 시 노드의 체인 ID를 확인한다.
 * <ul>
 * <li>체인 ID가 설정과 다르면 {@link ChainIdMismatchException}으로 앱 시작을 막는다.</li>
 * <li>노드에 연결할 수 없으면 경고만 남기고 시작한다. 조회 API와 헬스 체크는 동작하고, 첫 전송 전에 다시 확인한다.</li>
 * </ul>
 */
@Component
public class ChainIdStartupCheck implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ChainIdStartupCheck.class);

	private final ChainIdGuard chainIdGuard;

	public ChainIdStartupCheck(ChainIdGuard chainIdGuard) {
		this.chainIdGuard = chainIdGuard;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			chainIdGuard.verify();
			log.info("Chain id verified: {}", chainIdGuard.expectedChainId());
		}
		catch (ChainUnavailableException ex) {
			log.warn("Chain node unavailable at startup; chain id {} will be verified before the first transfer",
					chainIdGuard.expectedChainId());
		}
	}

}
