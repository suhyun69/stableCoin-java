package com.stablecoin.chain.adapter.out.signer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.stablecoin.chain.application.SignerNotConfiguredException;
import com.stablecoin.chain.application.port.out.TransactionSignerPort;
import com.stablecoin.chain.domain.UnsignedTransaction;

/**
 * web3j로 EIP-1559 트랜잭션에 서명하는 {@link TransactionSignerPort} 구현.
 * <p>
 * 개인키는 chain.signer-private-key 로 받는다. 비어 있으면 앱은 시작하되 전송 시
 * {@link SignerNotConfiguredException}을 던지고, 형식이 잘못되었으면 앱 시작을 막는다.
 * 개인키 값은 로그나 예외 메시지에 남기지 않는다.
 */
@Component
public class Web3jTransactionSigner implements TransactionSignerPort {

	private static final Logger log = LoggerFactory.getLogger(Web3jTransactionSigner.class);

	private final Credentials credentials;

	public Web3jTransactionSigner(@Value("${chain.signer-private-key:}") String privateKey) {
		this.credentials = load(privateKey);
	}

	private static Credentials load(String privateKey) {
		if (privateKey == null || privateKey.isBlank()) {
			log.warn("chain.signer-private-key is not set; transfers are disabled");
			return null;
		}
		if (!privateKey.strip().matches("^(0x)?[0-9a-fA-F]{64}$")) {
			throw new IllegalStateException("chain.signer-private-key must be a 32-byte hex string");
		}
		Credentials credentials = Credentials.create(privateKey.strip());
		log.info("Transaction signer address: {}", credentials.getAddress());
		return credentials;
	}

	@Override
	public String address() {
		return credentials().getAddress();
	}

	@Override
	public String sign(UnsignedTransaction tx) {
		RawTransaction raw = RawTransaction.createEtherTransaction(tx.chainId(), tx.nonce(), tx.gasLimit(), tx.to(),
				tx.value(), tx.maxPriorityFeePerGas(), tx.maxFeePerGas());
		return Numeric.toHexString(TransactionEncoder.signMessage(raw, credentials()));
	}

	private Credentials credentials() {
		if (credentials == null) {
			throw new SignerNotConfiguredException();
		}
		return credentials;
	}

	@Override
	public String toString() {
		return "Web3jTransactionSigner[address=" + ((credentials != null) ? credentials.getAddress() : "none") + "]";
	}

}
