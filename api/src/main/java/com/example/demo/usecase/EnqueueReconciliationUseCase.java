package com.example.demo.usecase;

import com.example.demo.usecase.dto.ReconciliationAppResponse;

public interface EnqueueReconciliationUseCase {
  ReconciliationAppResponse enqueue(String fromBlock, String toBlock);
}
