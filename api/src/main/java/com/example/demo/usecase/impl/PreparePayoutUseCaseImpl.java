package com.example.demo.usecase.impl;

import com.example.demo.chain.application.port.out.*;
import com.example.demo.usecase.PreparePayoutUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "stablepay.chain.enabled", havingValue = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PreparePayoutUseCaseImpl implements PreparePayoutUseCase {
  private final EthChainPort chain;
  private final ChainTransactionStorePort store;
  private final TransactionTemplate tx;

  public PreparePayoutUseCaseImpl(
      EthChainPort chain, ChainTransactionStorePort store, PlatformTransactionManager manager) {
    this.chain = chain;
    this.store = store;
    this.tx = new TransactionTemplate(manager);
  }

  public void prepare(String payoutId) {
    var existing = store.find(payoutId);
    var transaction =
        existing.orElseGet(
            () -> {
              long initial = chain.pendingNonce();
              return tx.execute(status -> store.prepare(payoutId, chain.sender(), initial));
            });
    if ("CREATED".equals(transaction.status())) {
      var signed = chain.sign(transaction);
      tx.executeWithoutResult(status -> store.signed(payoutId, signed));
    }
  }
}
