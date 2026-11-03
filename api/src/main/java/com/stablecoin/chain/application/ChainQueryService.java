package com.stablecoin.chain.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.stablecoin.chain.application.port.in.ChainQueryUseCase;
import com.stablecoin.chain.application.port.out.ChainNodePort;
import com.stablecoin.chain.application.port.out.TransactionSignerPort;
import com.stablecoin.chain.domain.Account;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.ChainTransaction;
import com.stablecoin.chain.domain.TransactionDetail;

@Service
public class ChainQueryService implements ChainQueryUseCase {

	private final ChainNodePort chainNode;

	private final TransactionSignerPort signer;

	public ChainQueryService(ChainNodePort chainNode, TransactionSignerPort signer) {
		this.chainNode = chainNode;
		this.signer = signer;
	}

	@Override
	public long getChainId() {
		return chainNode.getChainId();
	}

	@Override
	public List<Account> getAccounts() {
		return chainNode.getAccounts()
			.stream()
			.map(address -> Account.of(address, chainNode.getBalance(address)))
			.toList();
	}

	@Override
	public List<Account> getSigners() {
		return signer.addresses().stream().map(address -> Account.of(address, chainNode.getBalance(address))).toList();
	}

	@Override
	public boolean canSign(String address) {
		return signer.canSign(address);
	}

	@Override
	public Optional<ChainReceipt> getReceipt(String txHash) {
		return chainNode.getReceipt(txHash);
	}

	@Override
	public boolean isKnownTransaction(String txHash) {
		return chainNode.getTransaction(txHash).isPresent();
	}

	@Override
	public Optional<ChainTransaction> findTransaction(String txHash) {
		return chainNode.getTransaction(txHash);
	}

	@Override
	public long getLatestBlockNumber() {
		return chainNode.getBlockNumber();
	}

	@Override
	public long getConfirmedNonce(String address) {
		return chainNode.getConfirmedNonce(address).longValueExact();
	}

	@Override
	public TransactionDetail getTransaction(String txHash) {
		ChainTransaction tx = chainNode.getTransaction(txHash)
			.orElseThrow(() -> new TransactionNotFoundException(txHash));
		ChainReceipt receipt = chainNode.getReceipt(txHash).orElse(null);
		Instant timestamp = (receipt != null) ? chainNode.getBlockTimestamp(receipt.blockNumber()) : null;
		return TransactionDetail.of(tx, receipt, timestamp);
	}

}
