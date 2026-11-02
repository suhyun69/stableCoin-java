package com.stablecoin.payout.adapter.out.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.stablecoin.chain.application.port.in.ChainQueryUseCase;
import com.stablecoin.chain.application.port.in.TransferUseCase;
import com.stablecoin.chain.domain.TransactionStatus;
import com.stablecoin.chain.domain.Transfer;
import com.stablecoin.payout.domain.Payout;

class ChainPayoutTransferAdapterTest {

	private static final String PAYOUT_WALLET = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String RECIPIENT = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private final TransferUseCase transferUseCase = mock(TransferUseCase.class);

	private final ChainQueryUseCase chainQuery = mock(ChainQueryUseCase.class);

	@Test
	void sendsFromPayoutWalletToRecipient() {
		given(chainQuery.canSign(PAYOUT_WALLET)).willReturn(true);
		BigInteger wei = new BigInteger("25500000000000000000");
		given(transferUseCase.transfer(PAYOUT_WALLET, RECIPIENT, wei)).willReturn(
				new Transfer("0xabc", PAYOUT_WALLET, RECIPIENT, wei.toString(), "25.5", 0L, 1L, TransactionStatus.SUCCESS));
		ChainPayoutTransferAdapter adapter = new ChainPayoutTransferAdapter(transferUseCase, chainQuery, PAYOUT_WALLET);

		String txHash = adapter.transfer(Payout.requested("merchant_demo_01", RECIPIENT, wei, "k"));

		assertThat(txHash).isEqualTo("0xabc");
	}

	@Test
	void payoutWalletWithoutKeyStopsStartup() {
		given(chainQuery.canSign(PAYOUT_WALLET)).willReturn(false);

		assertThatThrownBy(() -> new ChainPayoutTransferAdapter(transferUseCase, chainQuery, PAYOUT_WALLET))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(PAYOUT_WALLET);
	}

}
