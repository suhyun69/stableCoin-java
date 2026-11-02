package com.stablecoin.chain.adapter.out.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.transaction.type.Transaction1559;
import org.web3j.crypto.transaction.type.TransactionType;

import com.stablecoin.chain.application.UnknownSignerException;
import com.stablecoin.chain.domain.UnsignedTransaction;

class AnvilDevWalletSignerTest {

	private static final String ANVIL_MNEMONIC = "test test test test test test test test test test test junk";

	/** anvil이 시작할 때 출력하는 기본 지갑 0, 1, 9번 주소 */
	private static final String ANVIL_0 = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String ANVIL_1 = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final String ANVIL_9 = "0xa0ee7a142d267c1f36714e4a8f75612f20a79720";

	private final AnvilDevWalletSigner signer = new AnvilDevWalletSigner(ANVIL_MNEMONIC, 10, 31337L);

	@Test
	void derivesTheSameWalletsAsAnvilInOrder() {
		assertThat(signer.addresses()).hasSize(10).startsWith(ANVIL_0, ANVIL_1).endsWith(ANVIL_9);
	}

	@Test
	void canSignIgnoresAddressCase() {
		assertThat(signer.canSign("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")).isTrue();
		assertThat(signer.canSign("0x000000000000000000000000000000000000dEaD")).isFalse();
	}

	@Test
	void signsEip1559TransactionRecoverableToFromWallet() throws Exception {
		UnsignedTransaction tx = new UnsignedTransaction(31337L, BigInteger.valueOf(7), ANVIL_0,
				new BigInteger("1500000000000000000"), BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L),
				BigInteger.valueOf(2_750_000_000L));

		String signed = signer.sign(ANVIL_1, tx);

		assertThat(signed).startsWith("0x02");
		RawTransaction decoded = TransactionDecoder.decode(signed);
		assertThat(decoded.getType()).isEqualTo(TransactionType.EIP1559);
		assertThat(decoded.getNonce()).isEqualTo(BigInteger.valueOf(7));
		assertThat(decoded.getTo()).isEqualTo(ANVIL_0);
		assertThat(decoded.getValue()).isEqualTo(new BigInteger("1500000000000000000"));
		Transaction1559 body = (Transaction1559) decoded.getTransaction();
		assertThat(body.getChainId()).isEqualTo(31337L);
		assertThat(body.getGasLimit()).isEqualTo(BigInteger.valueOf(21000));
		assertThat(((SignedRawTransaction) decoded).getFrom()).isEqualTo(ANVIL_1);
	}

	@Test
	void unknownWalletCannotSign() {
		UnsignedTransaction tx = new UnsignedTransaction(31337L, BigInteger.ZERO, ANVIL_0, BigInteger.ONE,
				BigInteger.valueOf(21000), BigInteger.ONE, BigInteger.TWO);

		assertThatThrownBy(() -> signer.sign("0x000000000000000000000000000000000000dEaD", tx))
			.isInstanceOf(UnknownSignerException.class);
	}

	@Test
	void refusesNonLocalChain() {
		assertThatThrownBy(() -> new AnvilDevWalletSigner(ANVIL_MNEMONIC, 10, 1L))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("31337");
	}

	@Test
	void rejectsInvalidMnemonic() {
		assertThatThrownBy(() -> new AnvilDevWalletSigner("not a valid mnemonic", 10, 31337L))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void customCount() {
		assertThat(new AnvilDevWalletSigner(ANVIL_MNEMONIC, 3, 31337L).addresses()).hasSize(3);
	}

	@Test
	void toStringDoesNotRevealKeys() {
		assertThat(signer.toString()).isEqualTo("AnvilDevWalletSigner[wallets=10]");
	}

}
