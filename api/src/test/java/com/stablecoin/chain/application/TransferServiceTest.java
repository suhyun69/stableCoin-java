package com.stablecoin.chain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.stablecoin.chain.application.port.in.SendTransactionUseCase;
import com.stablecoin.chain.application.port.out.ChainNodePort;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.chain.domain.TransactionStatus;
import com.stablecoin.chain.domain.Transfer;

class TransferServiceTest {

	private static final String FROM = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String TO = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final BigInteger WEI = new BigInteger("1500000000000000000");

	private static final SignedTransaction SIGNED = new SignedTransaction(FROM, TO, WEI, 4, "0x02signed", "0xabc");

	private final SendTransactionUseCase sendTransaction = mock(SendTransactionUseCase.class);

	private final ChainNodePort chainNode = mock(ChainNodePort.class);

	private final TransferService transferService = new TransferService(sendTransaction, new WalletSendLocks(),
			chainNode, Duration.ofMillis(300));

	@Test
	void signsThenBroadcastsAndReturnsTheReceipt() {
		given(sendTransaction.sign(FROM, TO, WEI, 0)).willReturn(SIGNED);
		given(chainNode.getReceipt("0xabc")).willReturn(Optional.of(
				new ChainReceipt(5L, true, BigInteger.valueOf(21000), BigInteger.valueOf(1_875_000_000L))));

		Transfer transfer = transferService.transfer(FROM, TO, WEI);

		InOrder order = inOrder(sendTransaction);
		order.verify(sendTransaction).sign(FROM, TO, WEI, 0);
		order.verify(sendTransaction).broadcast(SIGNED);
		assertThat(transfer).isEqualTo(
				new Transfer("0xabc", FROM, TO, WEI.toString(), "1.5", 4L, 5L, TransactionStatus.SUCCESS));
	}

	@Test
	void receiptNotAvailableBeforeTimeoutIsPending() {
		given(sendTransaction.sign(FROM, TO, WEI, 0)).willReturn(SIGNED);
		given(chainNode.getReceipt("0xabc")).willReturn(Optional.empty());

		Transfer transfer = transferService.transfer(FROM, TO, WEI);

		assertThat(transfer.status()).isEqualTo(TransactionStatus.PENDING);
		assertThat(transfer.blockNumber()).isNull();
		then(sendTransaction).should().broadcast(SIGNED);
	}

	@Test
	void waitsUntilReceiptIsAvailable() {
		given(sendTransaction.sign(FROM, TO, WEI, 0)).willReturn(SIGNED);
		given(chainNode.getReceipt("0xabc")).willReturn(Optional.empty(), Optional
			.of(new ChainReceipt(2L, false, BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L))));

		Transfer transfer = transferService.transfer(FROM, TO, WEI);

		assertThat(transfer.status()).isEqualTo(TransactionStatus.FAILED);
		assertThat(transfer.blockNumber()).isEqualTo(2L);
	}

}
