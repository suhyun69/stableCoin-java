package com.stablecoin.chain.adapter.out.signer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.stablecoin.chain.application.UnknownSignerException;
import com.stablecoin.chain.application.port.out.TransactionSignerPort;
import com.stablecoin.chain.domain.UnsignedTransaction;

/**
 * anvil 기본 지갑의 키로 서명하는 개발용 {@link TransactionSignerPort} 구현.
 * <p>
 * anvil은 니모닉에서 BIP-44 경로 m/44'/60'/0'/0/i 로 지갑을 만든다. 같은 니모닉과 경로로 앱 시작 시 키를 계산해
 * 메모리에만 두므로, anvil을 다시 띄워도 별도 설정 없이 그대로 쓸 수 있다. 키는 로그나 응답에 남기지 않는다.
 * <p>
 * 기본 니모닉은 누구나 아는 공개 값이므로 로컬 체인(chain.id 31337)에서만 허용하고, 다른 체인이면 앱 시작을 막는다.
 */
@Component
public class AnvilDevWalletSigner implements TransactionSignerPort {

	/** anvil, Hardhat 로컬 체인 ID */
	static final long LOCAL_CHAIN_ID = 31337L;

	private static final Logger log = LoggerFactory.getLogger(AnvilDevWalletSigner.class);

	private static final int HARDENED = Bip32ECKeyPair.HARDENED_BIT;

	/** 소문자 주소 → 키. 지갑 순서(anvil의 0, 1, 2 ...)를 유지한다. */
	private final Map<String, Credentials> wallets;

	public AnvilDevWalletSigner(@Value("${chain.dev-wallets.mnemonic}") String mnemonic,
			@Value("${chain.dev-wallets.count:10}") int count, @Value("${chain.id}") long chainId) {
		if (chainId != LOCAL_CHAIN_ID) {
			throw new IllegalStateException("anvil dev wallets are only allowed on the local chain (chain.id "
					+ LOCAL_CHAIN_ID + "), but chain.id is " + chainId);
		}
		this.wallets = derive(mnemonic.strip(), count);
		log.info("Loaded {} anvil dev wallets (first: {})", wallets.size(), wallets.keySet().iterator().next());
	}

	private static Map<String, Credentials> derive(String mnemonic, int count) {
		if (!MnemonicUtils.validateMnemonic(mnemonic)) {
			throw new IllegalStateException("chain.dev-wallets.mnemonic is not a valid BIP-39 mnemonic");
		}
		if (count < 1) {
			throw new IllegalStateException("chain.dev-wallets.count must be at least 1");
		}
		Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(MnemonicUtils.generateSeed(mnemonic, ""));
		Map<String, Credentials> wallets = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			int[] path = { 44 | HARDENED, 60 | HARDENED, HARDENED, 0, i };
			Credentials credentials = Credentials.create(Bip32ECKeyPair.deriveKeyPair(master, path));
			wallets.put(credentials.getAddress().toLowerCase(Locale.ROOT), credentials);
		}
		return Collections.unmodifiableMap(wallets);
	}

	@Override
	public List<String> addresses() {
		return List.copyOf(wallets.keySet());
	}

	@Override
	public boolean canSign(String address) {
		return wallets.containsKey(address.toLowerCase(Locale.ROOT));
	}

	@Override
	public String sign(String from, UnsignedTransaction tx) {
		Credentials credentials = wallets.get(from.toLowerCase(Locale.ROOT));
		if (credentials == null) {
			throw new UnknownSignerException(from);
		}
		RawTransaction raw = RawTransaction.createEtherTransaction(tx.chainId(), tx.nonce(), tx.gasLimit(), tx.to(),
				tx.value(), tx.maxPriorityFeePerGas(), tx.maxFeePerGas());
		return Numeric.toHexString(TransactionEncoder.signMessage(raw, credentials));
	}

	@Override
	public String toString() {
		return "AnvilDevWalletSigner[wallets=" + wallets.size() + "]";
	}

}
