package com.stablecoin.chain.domain;

/**
 * 트랜잭션 상태.
 */
public enum TransactionStatus {

	/** 블록에 포함되어 성공 */
	SUCCESS,

	/** 블록에 포함되었지만 실행 실패 */
	FAILED,

	/** 아직 블록에 포함되지 않음 */
	PENDING;

	public static TransactionStatus of(ChainReceipt receipt) {
		if (receipt == null) {
			return PENDING;
		}
		return receipt.success() ? SUCCESS : FAILED;
	}

}
