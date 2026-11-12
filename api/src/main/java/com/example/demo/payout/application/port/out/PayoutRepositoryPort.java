package com.example.demo.payout.application.port.out;

import com.example.demo.payout.domain.Payout;
import com.example.demo.payout.domain.PayoutRequest;

public interface PayoutRepositoryPort {
  Payout createRequested(PayoutRequest request);
}
