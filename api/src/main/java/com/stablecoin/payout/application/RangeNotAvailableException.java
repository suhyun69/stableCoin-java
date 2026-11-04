package com.stablecoin.payout.application;

/**
 * 체인이 아직 대사 범위의 끝 블록에 이르지 않았을 때 발생한다. 시간이 지나면 해결되므로 다시 시도한다.
 */
public class RangeNotAvailableException extends RuntimeException {

	public RangeNotAvailableException(long head, long toBlock) {
		super("chain head " + head + " has not reached block " + toBlock);
	}

}
