package com.stablecoin.chain.application.port.in;

import java.math.BigInteger;

import com.stablecoin.chain.application.ChainException;
import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.chain.application.UnknownSignerException;
import com.stablecoin.chain.application.WalletSendLocks;
import com.stablecoin.chain.domain.SignedTransaction;

/**
 * 서명과 전송을 나눠서 쓰는 입력 포트. 서명한 트랜잭션을 먼저 저장해 두고 보내야 할 때 쓴다.
 * 같은 지갑의 nonce가 겹치지 않도록 sign 부터 broadcast 까지 {@link WalletSendLocks} 안에서 호출해야 한다.
 */
public interface SendTransactionUseCase {

	/**
	 * from 지갑에서 to 로 wei 를 보내는 EIP-1559 트랜잭션을 만들어 서명한다. 노드에 보내지는 않는다.
	 * @param minNonce 이 값보다 작은 nonce는 쓰지 않는다 (서명만 하고 아직 못 보낸 트랜잭션이 있을 때)
	 * @throws UnknownSignerException 앱이 from 지갑의 키를 갖고 있지 않은 경우
	 * @throws ChainException 노드가 거부한 경우 (잔액 부족 등)
	 */
	SignedTransaction sign(String from, String to, BigInteger wei, long minNonce);

	/**
	 * 서명한 트랜잭션을 노드에 보낸다. 같은 트랜잭션을 다시 보내도 안전하다: 노드가 이미 그 해시를 알고 있으면 성공으로 본다.
	 * @return 트랜잭션 해시
	 * @throws ChainException 노드가 거부한 경우
	 * @throws ChainUnavailableException 노드에 연결할 수 없는 경우
	 */
	String broadcast(SignedTransaction transaction);

}
