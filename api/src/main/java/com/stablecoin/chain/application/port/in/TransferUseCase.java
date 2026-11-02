package com.stablecoin.chain.application.port.in;

import java.math.BigInteger;

import com.stablecoin.chain.domain.Transfer;

/**
 * ETH 전송 입력 포트. 보내는 지갑은 앱의 서명 지갑으로 고정된다.
 */
public interface TransferUseCase {

	Transfer transfer(String to, BigInteger wei);

}
