package com.example.demo.reconciliation.application.port.out;

import com.example.demo.reconciliation.domain.ReconciliationRequestedEvent;

public interface ReconciliationJobsPort {
  void enqueue(ReconciliationRequestedEvent event);
}
