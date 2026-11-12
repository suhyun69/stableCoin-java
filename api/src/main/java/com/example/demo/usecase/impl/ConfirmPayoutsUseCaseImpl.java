package com.example.demo.usecase.impl;

import com.example.demo.chain.application.port.out.*;
import com.example.demo.chain.domain.ConfirmationDecision;
import com.example.demo.usecase.ConfirmPayoutsUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "stablepay.chain.enabled", havingValue = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ConfirmPayoutsUseCaseImpl implements ConfirmPayoutsUseCase {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ConfirmPayoutsUseCaseImpl.class);
  private final EthChainPort chain;
  private final ChainTransactionStorePort transactions;
  private final ConfirmationStorePort store;
  private final TransactionTemplate tx;
  private final long required;

  public ConfirmPayoutsUseCaseImpl(
      EthChainPort chain,
      ChainTransactionStorePort transactions,
      ConfirmationStorePort store,
      PlatformTransactionManager manager,
      @Value("${stablepay.chain.confirmations:2}") long required) {
    if (required < 1) throw new IllegalArgumentException("required confirmations must be positive");
    this.chain = chain;
    this.transactions = transactions;
    this.store = store;
    this.tx = new TransactionTemplate(manager);
    this.required = required;
  }

  public int confirm() {
    int count = 0;
    for (String id : store.candidates(100)) {
      try {
        var saved = transactions.find(id).orElseThrow();
        var receipt = chain.receipt(saved.hash());
        if (receipt.isPresent()) {
          var observed = chain.transaction(saved.hash()).orElse(null);
          var r = receipt.get();
          var decision =
              ConfirmationDecision.evaluate(
                  saved, observed, r, chain.head(), chain.blockHash(r.blockNumber()), required);
          tx.executeWithoutResult(status -> store.apply(saved, r, decision));
          count++;
        }
      } catch (RuntimeException e) {
        log.warn("payout confirmation failed for {}: {}", id, e.getMessage());
      } finally {
        store.touch(id);
      }
    }
    return count;
  }
}
