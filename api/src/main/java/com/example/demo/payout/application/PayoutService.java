package com.example.demo.payout.application;

import com.example.demo.payout.application.port.out.PayoutRepositoryPort;
import com.example.demo.payout.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PayoutService {
  private final PayoutRepositoryPort repository;

  public PayoutService(PayoutRepositoryPort repository) {
    this.repository = repository;
  }

  public Payout create(PayoutRequest request) {
    return repository.createRequested(request);
  }
}
