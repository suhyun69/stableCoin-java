package com.example.demo.controller;

import com.example.demo.controller.dto.*;
import com.example.demo.usecase.EnqueueReconciliationUseCase;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "stablepay.messaging.enabled", havingValue = "true")
public class ReconciliationController {
  private final EnqueueReconciliationUseCase enqueue;

  public ReconciliationController(EnqueueReconciliationUseCase enqueue) {
    this.enqueue = enqueue;
  }

  @PostMapping("/internal/reconciliation/runs")
  public ResponseEntity<ReconciliationWebResponse> enqueue(
      @Valid @RequestBody ReconciliationWebRequest request) {
    var response = enqueue.enqueue(request.fromBlock(), request.toBlock());
    return ResponseEntity.accepted()
        .body(
            new ReconciliationWebResponse(
                response.runId(), response.accepted(), response.fromBlock(), response.toBlock()));
  }
}
