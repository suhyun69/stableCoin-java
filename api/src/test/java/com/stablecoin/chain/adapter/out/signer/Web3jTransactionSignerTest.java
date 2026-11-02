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

import com.stablecoin.chain.application.SignerNotConfiguredException;
import com.stablecoin.chain.domain.UnsignedTransaction;

class Web3jTransactionSignerTest {

	/** anvil 기본 지갑 0번의 개인키. anvil이 시작할 때 출력하는 공개된 테스트 키다. */
	private static final String ANVIL_KEY_0 = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

	private static final String ANVIL_ADDRESS_0 = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String TO = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	@Test
	void addressIsDerivedFromPrivateKey() {
		assertThat(new Web3jTransactionSigner(ANVIL_KEY_0).address()).isEqualTo(ANVIL_ADDRESS_0);
	}

	@Test
	void signsEip1559TransactionRecoverableToSignerAddress() throws Exception {
		Web3jTransactionSigner signer = new Web3jTransactionSigner(ANVIL_KEY_0);
		UnsignedTransaction tx = new UnsignedTransaction(31337L, BigInteger.valueOf(7), TO,
				new BigInteger("1500000000000000000"), BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L),
				BigInteger.valueOf(2_750_000_000L));

		String signed = signer.sign(tx);

		assertThat(signed).startsWith("0x02");
		RawTransaction decoded = TransactionDecoder.decode(signed);
		assertThat(decoded.getType()).isEqualTo(TransactionType.EIP1559);
		assertThat(decoded.getNonce()).isEqualTo(BigInteger.valueOf(7));
		assertThat(decoded.getTo()).isEqualTo(TO);
		assertThat(decoded.getValue()).isEqualTo(new BigInteger("1500000000000000000"));
		assertThat(decoded.getTransaction().getGasLimit()).isEqualTo(BigInteger.valueOf(21000));
		Transaction1559 body = (Transaction1559) decoded.getTransaction();
		assertThat(body.getChainId()).isEqualTo(31337L);
		assertThat(body.getMaxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(1_000_000_000L));
		assertThat(body.getMaxFeePerGas()).isEqualTo(BigInteger.valueOf(2_750_000_000L));
		assertThat(((SignedRawTransaction) decoded).getFrom()).isEqualTo(ANVIL_ADDRESS_0);
	}

	@Test
	void acceptsKeyWithoutPrefixAndSurroundingWhitespace() {
		assertThat(new Web3jTransactionSigner("  " + ANVIL_KEY_0.substring(2) + "\n").address())
			.isEqualTo(ANVIL_ADDRESS_0);
	}

	@Test
	void missingKeyDisablesSigning() {
		Web3jTransactionSigner signer = new Web3jTransactionSigner("");

		assertThatThrownBy(signer::address).isInstanceOf(SignerNotConfiguredException.class);
		assertThat(signer.toString()).contains("none");
	}

	@Test
	void malformedKeyFailsWithoutRevealingIt() {
		String malformed = "0x1234not-a-key";

		assertThatThrownBy(() -> new Web3jTransactionSigner(malformed)).isInstanceOf(IllegalStateException.class)
			.hasMessageNotContaining(malformed);
	}

	@Test
	void toStringDoesNotRevealPrivateKey() {
		assertThat(new Web3jTransactionSigner(ANVIL_KEY_0).toString()).doesNotContain(ANVIL_KEY_0.substring(2))
			.contains(ANVIL_ADDRESS_0);
	}

}
