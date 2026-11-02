package com.stablecoin.payout.application.port.out;

import com.stablecoin.payout.domain.Payout;

/**
 * 지급 금액을 블록체인으로 보내는 출력 포트. 지금은 ETH로 보내며, ERC-20(USDC) 전송으로 바꿀 때 구현만 교체한다.
 */
public interface PayoutTransferPort {

	/**
	 * @return 트랜잭션 해시
	 */
	String transfer(Payout payout);

}
