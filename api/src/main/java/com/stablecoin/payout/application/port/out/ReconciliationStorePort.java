package com.stablecoin.payout.application.port.out;

import java.util.Collection;
import java.util.List;

import com.stablecoin.payout.domain.LedgerEntry;
import com.stablecoin.payout.domain.ReconciliationFinding;

/**
 * 대사의 출력 포트. 호출한 쪽의 트랜잭션이 있으면 참여하고, 없으면 바로 커밋한다.
 */
public interface ReconciliationStorePort {

	/**
	 * 블록 범위에 포함된 것으로 기록된 장부와, 범위 안의 체인 전송 해시를 가진 장부.
	 */
	List<LedgerEntry> readLedger(long fromBlock, long toBlock, Collection<String> transactionHashes);

	/**
	 * 불일치를 OPEN 으로 남긴다. 같은 문제가 이미 OPEN 이면 아무것도 하지 않는다.
	 */
	void recordOpen(ReconciliationFinding finding);

	/**
	 * 자동으로 해결한 불일치를 RESOLVED 로 남긴다.
	 */
	void recordResolved(ReconciliationFinding finding);

}
