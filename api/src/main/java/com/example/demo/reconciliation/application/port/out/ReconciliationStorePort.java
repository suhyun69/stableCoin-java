package com.example.demo.reconciliation.application.port.out;

import com.example.demo.reconciliation.domain.*;
import java.util.List;
import java.util.Set;

public interface ReconciliationStorePort {
  void lock();

  Set<String> historicalSenders();

  List<ReconciliationEntry> ledger(BlockRange range, List<String> hashes, String sender);

  void save(ReconciliationFinding finding, boolean resolved);

  void resolveAbsent(
      List<ReconciliationEntry> ledger,
      List<ObservedEthTransfer> observed,
      List<ReconciliationFinding> current);
}
