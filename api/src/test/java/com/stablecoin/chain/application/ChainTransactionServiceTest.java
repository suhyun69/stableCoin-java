package com.stablecoin.chain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.stablecoin.chain.application.port.out.ChainNodePort;
import com.stablecoin.chain.application.port.out.TransactionSignerPort;
import com.stablecoin.chain.domain.ChainTransaction;
import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.chain.domain.UnsignedTransaction;

/**
 * 포트를 가짜로 바꿔 끼워 노드와 개인키 없이 서명·전송 절차를 확인한다.
 */
class ChainTransactionServiceTest {

	private static final String FROM = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String TO = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final BigInteger WEI = new BigInteger("1500000000000000000");

	private static final SignedTransaction SIGNED = new SignedTransaction(FROM, TO, WEI, 4, "0x02signed", "0xabc");

	private final ChainNodePort chainNode = mock(ChainNodePort.class);

	private final TransactionSignerPort signer = mock(TransactionSignerPort.class);

	private final ChainTransactionService service = new ChainTransactionService(chainNode, signer,
			new ChainIdGuard(chainNode, 31337L));

	@BeforeEach
	void setUp() {
		given(signer.canSign(FROM)).willReturn(true);
		given(chainNode.getChainId()).willReturn(31337L);
		given(chainNode.getPendingNonce(FROM)).willReturn(BigInteger.valueOf(4));
		given(chainNode.estimateGas(FROM, TO, WEI)).willReturn(BigInteger.valueOf(21000));
		given(chainNode.getMaxPriorityFeePerGas()).willReturn(BigInteger.valueOf(1_000_000_000L));
		given(chainNode.getLatestBaseFeePerGas()).willReturn(BigInteger.valueOf(875_000_000L));
		given(signer.sign(eq(FROM), any())).willReturn(SIGNED);
	}

	@Test
	void signsEip1559TransactionWithNodeValues() {
		SignedTransaction signed = service.sign(FROM, TO, WEI, 0);

		ArgumentCaptor<UnsignedTransaction> tx = ArgumentCaptor.forClass(UnsignedTransaction.class);
		then(signer).should().sign(eq(FROM), tx.capture());
		assertThat(tx.getValue()).isEqualTo(new UnsignedTransaction(31337L, BigInteger.valueOf(4), TO, WEI,
				BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L),
				// maxFee = baseFee * 2 + priorityFee
				BigInteger.valueOf(2_750_000_000L)));
		assertThat(signed).isEqualTo(SIGNED);
		then(chainNode).should(never()).sendRawTransaction(any());
	}

	@Test
	void minNonceIsUsedWhenAboveTheNodesPendingNonce() {
		service.sign(FROM, TO, WEI, 9);

		ArgumentCaptor<UnsignedTransaction> tx = ArgumentCaptor.forClass(UnsignedTransaction.class);
		then(signer).should().sign(eq(FROM), tx.capture());
		assertThat(tx.getValue().nonce()).isEqualTo(BigInteger.valueOf(9));
	}

	@Test
	void verifiesChainIdBeforeSigning() {
		service.sign(FROM, TO, WEI, 0);

		InOrder order = inOrder(chainNode, signer);
		order.verify(chainNode).getChainId();
		order.verify(signer).sign(eq(FROM), any());
	}

	@Test
	void chainIdMismatchStopsBeforeSigning() {
		given(chainNode.getChainId()).willReturn(1L);

		assertThatThrownBy(() -> service.sign(FROM, TO, WEI, 0)).isInstanceOf(ChainIdMismatchException.class);

		then(signer).should(never()).sign(any(), any());
	}

	@Test
	void unknownFromWalletStopsBeforeTouchingTheNode() {
		assertThatThrownBy(() -> service.sign("0x000000000000000000000000000000000000dead", TO, WEI, 0))
			.isInstanceOf(UnknownSignerException.class);

		then(chainNode).should(never()).getPendingNonce(any());
	}

	@Test
	void nodeRejectionWhileSigningIsPropagated() {
		given(chainNode.estimateGas(FROM, TO, WEI))
			.willThrow(new ChainException("eth_estimateGas: Insufficient funds for gas * price + value"));

		assertThatThrownBy(() -> service.sign(FROM, TO, WEI, 0)).isInstanceOf(ChainException.class);

		then(signer).should(never()).sign(any(), any());
	}

	@Test
	void broadcastSendsTheSignedRawTransaction() {
		given(chainNode.sendRawTransaction("0x02signed")).willReturn("0xabc");

		assertThat(service.broadcast(SIGNED)).isEqualTo("0xabc");
	}

	@Test
	void rebroadcastOfAnAlreadyKnownTransactionSucceeds() {
		given(chainNode.sendRawTransaction("0x02signed")).willThrow(new ChainException("transaction already imported"));
		given(chainNode.getTransaction("0xabc")).willReturn(Optional.of(new ChainTransaction("0xabc", FROM, TO, WEI, 4,
				21000, BigInteger.ONE, null, "0x")));

		assertThat(service.broadcast(SIGNED)).isEqualTo("0xabc");
	}

	@Test
	void broadcastRejectionOfAnUnknownTransactionIsPropagated() {
		given(chainNode.sendRawTransaction("0x02signed")).willThrow(new ChainException("nonce too low"));
		given(chainNode.getTransaction("0xabc")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.broadcast(SIGNED)).isInstanceOf(ChainException.class)
			.hasMessage("nonce too low");
	}

}
