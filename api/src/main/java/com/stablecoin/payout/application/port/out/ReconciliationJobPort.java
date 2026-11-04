package com.stablecoin.payout.application.port.out;

/**
 * 대사 작업을 대기열에 넣는 출력 포트.
 */
public interface ReconciliationJobPort {

	void enqueue(long fromBlock, long toBlock);

}
