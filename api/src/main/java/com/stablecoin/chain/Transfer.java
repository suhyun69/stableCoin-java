package com.stablecoin.chain;

import java.math.BigInteger;

import com.stablecoin.chain.ChainService.TransactionReceipt;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 전송 결과. 정밀도 손실을 막기 위해 금액은 문자열로 내보낸다.
 */
@Schema(description = "전송 결과")
public record Transfer(
		@Schema(description = "트랜잭션 해시") String txHash,
		@Schema(description = "보낸 지갑", example = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266") String from,
		@Schema(description = "받는 지갑", example = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8") String to,
		@Schema(description = "전송 금액 (wei)", example = "1500000000000000000") String amountWei,
		@Schema(description = "전송 금액 (ETH)", example = "1.5") String amountEth,
		@Schema(description = "포함된 블록 번호. 대기 중이면 null", example = "1") Long blockNumber,
		@Schema(description = "트랜잭션 상태") Status status) {

	public enum Status {

		/** 블록에 포함되어 성공 */
		SUCCESS,

		/** 블록에 포함되었지만 실행 실패 */
		FAILED,

		/** 아직 블록에 포함되지 않음 */
		PENDING

	}

	static Transfer of(String txHash, String from, String to, BigInteger wei, TransactionReceipt receipt) {
		Long blockNumber = null;
		Status status = Status.PENDING;
		if (receipt != null) {
			blockNumber = Eth.fromHex(receipt.blockNumber()).longValueExact();
			status = "0x1".equals(receipt.status()) ? Status.SUCCESS : Status.FAILED;
		}
		return new Transfer(txHash, from, to, wei.toString(), Eth.format(wei), blockNumber, status);
	}

}
