package com.stablecoin.chain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.stablecoin.chain.application.port.out.ChainNodePort;
import com.stablecoin.chain.application.port.out.TransactionSignerPort;
import com.stablecoin.chain.domain.Account;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.ChainTransaction;
import com.stablecoin.chain.domain.TransactionDetail;
import com.stablecoin.chain.domain.TransactionStatus;

class ChainQueryServiceTest {

	private static final String FROM = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String TO = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private final ChainNodePort chainNode = mock(ChainNodePort.class);

	private final TransactionSignerPort signer = mock(TransactionSignerPort.class);

	private final ChainQueryService service = new ChainQueryService(chainNode, signer);

	@Test
	void accountsWithBalances() {
		given(chainNode.getAccounts()).willReturn(List.of(FROM));
		given(chainNode.getBalance(FROM)).willReturn(new BigInteger("10000000000000000000000"));

		assertThat(service.getAccounts()).containsExactly(new Account(FROM, "10000000000000000000000", "10000"));
	}

	@Test
	void signersWithBalances() {
		given(signer.addresses()).willReturn(List.of(FROM, TO));
		given(chainNode.getBalance(FROM)).willReturn(new BigInteger("1500000000000000000"));
		given(chainNode.getBalance(TO)).willReturn(BigInteger.ZERO);

		assertThat(service.getSigners()).containsExactly(new Account(FROM, "1500000000000000000", "1.5"),
				new Account(TO, "0", "0"));
	}

	@Test
	void minedTransactionIncludesReceiptAndTimestamp() {
		given(chainNode.getTransaction("0xabc")).willReturn(Optional.of(new ChainTransaction("0xabc", FROM, TO,
				new BigInteger("1500000000000000000"), 3L, 21001L, BigInteger.valueOf(2_000_000_000L), 5L, "0x")));
		given(chainNode.getReceipt("0xabc")).willReturn(
				Optional.of(new ChainReceipt(5L, true, BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L))));
		given(chainNode.getBlockTimestamp(5L)).willReturn(Instant.ofEpochSecond(0x6730c800L));

		assertThat(service.getTransaction("0xabc")).isEqualTo(new TransactionDetail("0xabc",
				TransactionStatus.SUCCESS, 5L, Instant.ofEpochSecond(0x6730c800L), FROM, TO, "1500000000000000000",
				"1.5", 3L, 21001L, 21000L, "1000000000", "21000000000000", "0.000021", "0x"));
	}

	@Test
	void pendingTransactionHasNoBlockValues() {
		given(chainNode.getTransaction("0xabc")).willReturn(Optional.of(new ChainTransaction("0xabc", FROM, TO,
				BigInteger.ONE, 0L, 21001L, BigInteger.valueOf(2_000_000_000L), null, "0x")));
		given(chainNode.getReceipt("0xabc")).willReturn(Optional.empty());

		TransactionDetail detail = service.getTransaction("0xabc");

		assertThat(detail.status()).isEqualTo(TransactionStatus.PENDING);
		assertThat(detail.blockNumber()).isNull();
		assertThat(detail.timestamp()).isNull();
		assertThat(detail.gasUsed()).isNull();
		assertThat(detail.feeWei()).isNull();
		assertThat(detail.gasPriceWei()).isEqualTo("2000000000");
		then(chainNode).should(never()).getBlockTimestamp(org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void unknownTransactionThrowsNotFound() {
		given(chainNode.getTransaction("0xabc")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.getTransaction("0xabc")).isInstanceOf(TransactionNotFoundException.class);
	}

}
