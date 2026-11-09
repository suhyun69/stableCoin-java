package com.example.demo.payout.application.port.out;
import com.example.demo.payout.domain.PayoutStatusView;
import java.util.Optional;
public interface PayoutStatusReaderPort { Optional<PayoutStatusView> findById(String id); }
