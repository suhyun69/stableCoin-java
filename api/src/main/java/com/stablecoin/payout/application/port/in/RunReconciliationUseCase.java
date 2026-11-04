package com.stablecoin.payout.application.port.in;

import com.stablecoin.payout.application.InvalidBlockRangeException;
import com.stablecoin.payout.application.RangeNotAvailableException;

/**
 * 블록 범위의 지급 지갑 전송을 장부와 비교하는 입력 포트.
 */
public interface RunReconciliationUseCase {

	/**
	 * 대사 작업을 대기열에 넣는다.
	 * @throws InvalidBlockRangeException 범위가 잘못됐거나 한 번에 처리할 수 있는 크기를 넘은 경우
	 */
	void enqueue(long fromBlock, long toBlock);

	/**
	 * 대사를 실행한다.
	 * @return 찾은 불일치 수
	 * @throws RangeNotAvailableException 체인이 아직 toBlock 에 이르지 않은 경우
	 */
	int run(long fromBlock, long toBlock);

}
