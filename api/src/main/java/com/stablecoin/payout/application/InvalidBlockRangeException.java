package com.stablecoin.payout.application;

/**
 * 대사 블록 범위가 잘못됐을 때 발생한다.
 */
public class InvalidBlockRangeException extends RuntimeException {

	public InvalidBlockRangeException(String message) {
		super(message);
	}

}
