package com.stablecoin.chain;

import com.stablecoin.chain.ChainService.TransactionReceipt;

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

	static TransactionStatus of(TransactionReceipt receipt) {
		if (receipt == null) {
			return PENDING;
		}
		return "0x1".equals(receipt.status()) ? SUCCESS : FAILED;
	}

}
