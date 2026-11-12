package com.example.demo.chain.application.port.out;

import com.example.demo.chain.domain.*;
import java.util.List;

public interface ConfirmationStorePort {
  List<String> candidates(int limit);

  void apply(ChainTransaction expected, EthReceipt receipt, ConfirmationDecision decision);

  void touch(String payoutId);
}
