package com.stablecoin.chain;

import java.math.BigInteger;
import java.time.Instant;

import com.stablecoin.chain.ChainService.RpcBlock;
import com.stablecoin.chain.ChainService.RpcTransaction;
import com.stablecoin.chain.ChainService.TransactionReceipt;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 트랜잭션 상세 정보. 정밀도 손실을 막기 위해 금액은 문자열로 내보낸다.
 * 블록에 포함되기 전(PENDING)이면 블록, 가스 사용량, 수수료 관련 값은 null이다.
 */
@Schema(description = "트랜잭션 상세 정보")
public record TransactionDetail(
		@Schema(description = "트랜잭션 해시") String txHash,
		@Schema(description = "트랜잭션 상태") TransactionStatus status,
		@Schema(description = "포함된 블록 번호", example = "1") Long blockNumber,
		@Schema(description = "블록 생성 시각 (UTC)", example = "2026-11-02T12:00:00Z") Instant timestamp,
		@Schema(description = "보낸 지갑", example = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266") String from,
		@Schema(description = "받는 지갑. 컨트랙트 생성이면 null",
				example = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8") String to,
		@Schema(description = "전송 금액 (wei)", example = "1500000000000000000") String valueWei,
		@Schema(description = "전송 금액 (ETH)", example = "1.5") String valueEth,
		@Schema(description = "보낸 지갑의 nonce", example = "0") Long nonce,
		@Schema(description = "가스 한도", example = "21001") Long gasLimit,
		@Schema(description = "실제 사용한 가스", example = "21000") Long gasUsed,
		@Schema(description = "가스 가격 (wei). 블록 포함 후에는 실제 적용된 가격",
				example = "1875175000") String gasPriceWei,
		@Schema(description = "수수료 (wei) = gasUsed × gasPrice", example = "39378675000000") String feeWei,
		@Schema(description = "수수료 (ETH)", example = "0.000039378675") String feeEth,
		@Schema(description = "입력 데이터 (hex). 단순 ETH 전송이면 0x", example = "0x") String input) {

	static TransactionDetail of(RpcTransaction tx, TransactionReceipt receipt, RpcBlock block) {
		BigInteger value = Eth.fromHex(tx.value());
		Long gasUsed = null;
		String gasPrice = (tx.gasPrice() != null) ? Eth.fromHex(tx.gasPrice()).toString() : null;
		String feeWei = null;
		String feeEth = null;
		if (receipt != null) {
			BigInteger used = Eth.fromHex(receipt.gasUsed());
			BigInteger price = Eth.fromHex(receipt.effectiveGasPrice());
			BigInteger fee = used.multiply(price);
			gasUsed = used.longValueExact();
			gasPrice = price.toString();
			feeWei = fee.toString();
			feeEth = Eth.format(fee);
		}
		Instant timestamp = (block != null) ? Instant.ofEpochSecond(Eth.fromHexLong(block.timestamp())) : null;
		return new TransactionDetail(tx.hash(), TransactionStatus.of(receipt), Eth.fromHexLong(tx.blockNumber()),
				timestamp, tx.from(), tx.to(), value.toString(), Eth.format(value), Eth.fromHexLong(tx.nonce()),
				Eth.fromHexLong(tx.gas()), gasUsed, gasPrice, feeWei, feeEth, tx.input());
	}

}
