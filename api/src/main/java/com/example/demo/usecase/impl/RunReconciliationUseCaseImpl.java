package com.example.demo.usecase.impl;

import com.example.demo.chain.application.port.out.*;
import com.example.demo.chain.domain.*;
import com.example.demo.reconciliation.application.port.out.ReconciliationStorePort;
import com.example.demo.reconciliation.domain.*;
import com.example.demo.usecase.RunReconciliationUseCase;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "stablepay.chain.enabled", havingValue = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class RunReconciliationUseCaseImpl implements RunReconciliationUseCase {
  private final EthChainPort chain;
  private final ReconciliationStorePort store;
  private final ChainTransactionStorePort transactions;
  private final ConfirmationStorePort confirmations;
  private final RecoveryStorePort reviews;
  private final TransactionTemplate tx;
  private final long required;

  public RunReconciliationUseCaseImpl(
      EthChainPort chain,
      ReconciliationStorePort store,
      ChainTransactionStorePort transactions,
      ConfirmationStorePort confirmations,
      RecoveryStorePort reviews,
      PlatformTransactionManager manager,
      @Value("${stablepay.chain.confirmations:2}") long required) {
    if (required < 1) throw new IllegalArgumentException("required confirmations must be positive");
    this.chain = chain;
    this.store = store;
    this.transactions = transactions;
    this.confirmations = confirmations;
    this.reviews = reviews;
    this.tx = new TransactionTemplate(manager);
    this.required = required;
  }

  public int run(String fromBlock, String toBlock) {
    var range = BlockRange.parse(fromBlock, toBlock);
    long head = chain.head();
    if (range.to() > head) throw new IllegalStateException("chain has not reached requested range");
    String boundaryHash = chain.blockHash(range.to());
    List<ObservedEthTransfer> observed = new ArrayList<>();
    for (var transfer : chain.transfers(range.from(), range.to())) {
      if (!chain.sender().equalsIgnoreCase(transfer.from())) continue;
      var receipt =
          chain
              .receipt(transfer.hash())
              .orElseThrow(
                  () -> new IllegalStateException("receipt unavailable for mined transfer"));
      if (transfer.blockNumber() == null
          || receipt.blockNumber() != transfer.blockNumber()
          || !Objects.equals(receipt.blockHash(), transfer.blockHash())
          || !Objects.equals(receipt.hash(), transfer.hash()))
        throw new IllegalStateException("chain changed during reconciliation");
      observed.add(
          new ObservedEthTransfer(
              transfer.hash(),
              transfer.from(),
              transfer.to(),
              transfer.value(),
              transfer.nonce(),
              receipt.blockNumber(),
              receipt.blockHash(),
              receipt.successful(),
              head - receipt.blockNumber() + 1));
    }
    if (!Objects.equals(boundaryHash, chain.blockHash(range.to())))
      throw new IllegalStateException("chain reorganized during reconciliation");
    return tx.execute(
        status -> {
          store.lock();
          var ledger =
              store.ledger(
                  range, observed.stream().map(ObservedEthTransfer::hash).toList(), chain.sender());
          var findings = ReconciliationPolicy.classify(ledger, observed, required);
          for (var finding : findings) {
            boolean resolved = false;
            if (finding.payoutId() != null) {
              var saved = transactions.find(finding.payoutId()).orElseThrow();
              if ("STATUS_LAG".equals(finding.kind())) {
                var event =
                    observed.stream()
                        .filter(e -> e.hash().equals(finding.hash()))
                        .findFirst()
                        .orElseThrow();
                var receipt =
                    new EthReceipt(
                        event.hash(), event.successful(), event.blockNumber(), event.blockHash());
                var transfer =
                    new EthTransfer(
                        event.hash(),
                        event.sender(),
                        event.recipient(),
                        event.amount(),
                        event.nonce(),
                        event.blockNumber(),
                        event.blockHash());
                var decision =
                    ConfirmationDecision.evaluate(
                        saved, transfer, receipt, head, event.blockHash(), required);
                confirmations.apply(saved, receipt, decision);
                resolved = !"REQUIRES_REVIEW".equals(decision.kind());
              } else {
                reviews.review(saved, finding.reason());
              }
            }
            store.save(finding, resolved);
          }
          store.resolveAbsent(ledger, observed, findings);
          return findings.size();
        });
  }
}
