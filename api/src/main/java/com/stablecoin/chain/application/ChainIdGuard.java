package com.stablecoin.chain.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stablecoin.chain.application.port.out.ChainNodePort;

/**
 * 연결한 노드의 체인 ID가 설정(chain.id)과 같은지 확인한다.
 * 앱 시작 시 확인하고, 그때 노드에 연결할 수 없었으면 첫 서명 전에 다시 확인한다.
 */
@Service
public class ChainIdGuard {

	private final ChainNodePort chainNode;

	private final long expectedChainId;

	private volatile boolean verified;

	public ChainIdGuard(ChainNodePort chainNode, @Value("${chain.id}") long expectedChainId) {
		this.chainNode = chainNode;
		this.expectedChainId = expectedChainId;
	}

	public long expectedChainId() {
		return expectedChainId;
	}

	/**
	 * 노드에 체인 ID를 물어 확인한다.
	 * @throws ChainIdMismatchException 체인 ID가 다른 경우
	 * @throws ChainUnavailableException 노드에 연결할 수 없는 경우
	 */
	public void verify() {
		long actual = chainNode.getChainId();
		if (actual != expectedChainId) {
			throw new ChainIdMismatchException(expectedChainId, actual);
		}
		verified = true;
	}

	/**
	 * 아직 확인하지 못했으면 확인한다. 한 번 확인되면 다시 묻지 않는다.
	 */
	public void ensureVerified() {
		if (!verified) {
			verify();
		}
	}

}
