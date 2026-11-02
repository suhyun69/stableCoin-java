package com.stablecoin.payout.adapter.out.chain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.stablecoin.chain.application.port.in.ChainQueryUseCase;
import com.stablecoin.chain.application.port.in.TransferUseCase;
import com.stablecoin.payout.application.port.out.PayoutTransferPort;
import com.stablecoin.payout.domain.Payout;

/**
 * 지급 지갑(chain.payout-wallet)에서 ETH로 보내는 {@link PayoutTransferPort} 구현. chain 모듈의 전송을 그대로 쓴다.
 * 지급 지갑의 키를 앱이 갖고 있지 않으면 앱 시작을 막는다.
 */
@Component
class ChainPayoutTransferAdapter implements PayoutTransferPort {

	private final TransferUseCase transferUseCase;

	private final String payoutWallet;

	ChainPayoutTransferAdapter(TransferUseCase transferUseCase, ChainQueryUseCase chainQuery,
			@Value("${chain.payout-wallet}") String payoutWallet) {
		if (!chainQuery.canSign(payoutWallet)) {
			throw new IllegalStateException("chain.payout-wallet " + payoutWallet
					+ " is not a wallet the app can sign for (see GET /api/chain/signers)");
		}
		this.transferUseCase = transferUseCase;
		this.payoutWallet = payoutWallet;
	}

	@Override
	public String transfer(Payout payout) {
		return transferUseCase.transfer(payoutWallet, payout.recipientAddress(), payout.amountWei()).txHash();
	}

}
