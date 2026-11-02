package com.stablecoin.chain.application.port.in;

import java.math.BigInteger;

import com.stablecoin.chain.application.UnknownSignerException;
import com.stablecoin.chain.domain.Transfer;

/**
 * ETH 전송 입력 포트. from은 앱이 키를 갖고 있는 지갑이어야 한다.
 */
public interface TransferUseCase {

	/**
	 * @throws UnknownSignerException 앱이 from 지갑의 키를 갖고 있지 않은 경우
	 */
	Transfer transfer(String from, String to, BigInteger wei);

}
