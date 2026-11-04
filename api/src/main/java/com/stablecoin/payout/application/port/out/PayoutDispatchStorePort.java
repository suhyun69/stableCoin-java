package com.stablecoin.payout.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 소비자가 지급을 맡았다는 표시(payout_dispatches) 출력 포트. 호출한 쪽의 트랜잭션이 있으면 참여하고, 없으면 바로 커밋한다.
 */
public interface PayoutDispatchStorePort {

	/**
	 * 이 지급을 STARTED 로 표시한다. 이미 있으면 아무것도 하지 않는다 (지급마다 한 번만).
	 * @return 새로 만들었으면 true
	 */
	boolean startOnce(UUID payoutId);

	/**
	 * 트랜잭션 안에서 행을 잠근다. 같은 지급을 동시에 서명하지 않게 한다.
	 * @return 행이 있으면 true
	 */
	boolean lock(UUID payoutId);

	/**
	 * olderThan 보다 오래 전송을 마치지 못한 지급 (아직 REQUESTED 이고, 서명 전이거나 노드가 받았는지 모르는 경우).
	 */
	List<UUID> findStuck(Duration olderThan, int limit);

}
