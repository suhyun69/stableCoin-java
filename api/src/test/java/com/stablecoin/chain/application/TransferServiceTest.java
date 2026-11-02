package com.stablecoin.chain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.stablecoin.chain.application.port.out.ChainNodePort;
import com.stablecoin.chain.application.port.out.TransactionSignerPort;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.TransactionStatus;
import com.stablecoin.chain.domain.Transfer;
import com.stablecoin.chain.domain.UnsignedTransaction;

/**
 * 포트를 가짜로 바꿔 끼워 노드와 개인키 없이 전송 절차를 확인한다.
 */
class TransferServiceTest {

	private static final String SIGNER = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String TO = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final BigInteger WEI = new BigInteger("1500000000000000000");

	private final ChainNodePort chainNode = mock(ChainNodePort.class);

	private final TransactionSignerPort signer = mock(TransactionSignerPort.class);

	private final ChainIdGuard chainIdGuard = new ChainIdGuard(chainNode, 31337L);

	private TransferService transferService;

	@BeforeEach
	void setUp() {
		transferService = new TransferService(chainNode, signer, chainIdGuard, Duration.ofMillis(300));
		given(signer.address()).willReturn(SIGNER);
		given(chainNode.getChainId()).willReturn(31337L);
		given(chainNode.getPendingNonce(SIGNER)).willReturn(BigInteger.valueOf(4));
		given(chainNode.estimateGas(SIGNER, TO, WEI)).willReturn(BigInteger.valueOf(21000));
		given(chainNode.getMaxPriorityFeePerGas()).willReturn(BigInteger.valueOf(1_000_000_000L));
		given(chainNode.getLatestBaseFeePerGas()).willReturn(BigInteger.valueOf(875_000_000L));
		given(signer.sign(any())).willReturn("0x02signed");
		given(chainNode.sendRawTransaction("0x02signed")).willReturn("0xabc");
	}

	@Test
	void signsEip1559TransactionWithNodeValuesAndSendsIt() {
		given(chainNode.getReceipt("0xabc")).willReturn(Optional.of(
				new ChainReceipt(5L, true, BigInteger.valueOf(21000), BigInteger.valueOf(1_875_000_000L))));

		Transfer transfer = transferService.transfer(TO, WEI);

		ArgumentCaptor<UnsignedTransaction> signedTx = ArgumentCaptor.forClass(UnsignedTransaction.class);
		then(signer).should().sign(signedTx.capture());
		assertThat(signedTx.getValue()).isEqualTo(new UnsignedTransaction(31337L, BigInteger.valueOf(4), TO, WEI,
				BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L),
				// maxFee = baseFee * 2 + priorityFee
				BigInteger.valueOf(2_750_000_000L)));
		assertThat(transfer).isEqualTo(
				new Transfer("0xabc", SIGNER, TO, WEI.toString(), "1.5", 4L, 5L, TransactionStatus.SUCCESS));
	}

	@Test
	void verifiesChainIdBeforeSigning() {
		given(chainNode.getReceipt("0xabc")).willReturn(Optional.empty());

		transferService.transfer(TO, WEI);

		InOrder order = inOrder(chainNode, signer);
		order.verify(chainNode).getChainId();
		order.verify(signer).sign(any());
		order.verify(chainNode).sendRawTransaction("0x02signed");
	}

	@Test
	void chainIdMismatchStopsBeforeSigning() {
		given(chainNode.getChainId()).willReturn(1L);

		assertThatThrownBy(() -> transferService.transfer(TO, WEI)).isInstanceOf(ChainIdMismatchException.class);

		then(signer).should(never()).sign(any());
		then(chainNode).should(never()).sendRawTransaction(any());
	}

	@Test
	void missingSignerStopsBeforeTouchingTheNode() {
		given(signer.address()).willThrow(new SignerNotConfiguredException());

		assertThatThrownBy(() -> transferService.transfer(TO, WEI)).isInstanceOf(SignerNotConfiguredException.class);

		then(chainNode).should(never()).sendRawTransaction(any());
	}

	@Test
	void receiptNotAvailableBeforeTimeoutIsPending() {
		given(chainNode.getReceipt("0xabc")).willReturn(Optional.empty());

		Transfer transfer = transferService.transfer(TO, WEI);

		assertThat(transfer.status()).isEqualTo(TransactionStatus.PENDING);
		assertThat(transfer.blockNumber()).isNull();
	}

	@Test
	void waitsUntilReceiptIsAvailable() {
		given(chainNode.getReceipt("0xabc")).willReturn(Optional.empty(), Optional
			.of(new ChainReceipt(2L, false, BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L))));

		Transfer transfer = transferService.transfer(TO, WEI);

		assertThat(transfer.status()).isEqualTo(TransactionStatus.FAILED);
		assertThat(transfer.blockNumber()).isEqualTo(2L);
	}

	@Test
	void nodeRejectionIsPropagated() {
		given(chainNode.estimateGas(SIGNER, TO, WEI))
			.willThrow(new ChainException("eth_estimateGas: Insufficient funds for gas * price + value"));

		assertThatThrownBy(() -> transferService.transfer(TO, WEI)).isInstanceOf(ChainException.class);

		then(signer).should(never()).sign(any());
	}

}
