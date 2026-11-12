package com.example.demo.controller;

import com.example.demo.controller.dto.PayoutStatusWebResponse;
import com.example.demo.usecase.ReadPayoutStatusUseCase;
import org.springframework.web.bind.annotation.*;

@RestController
public class PayoutStatusController {
  private final ReadPayoutStatusUseCase read;

  public PayoutStatusController(ReadPayoutStatusUseCase read) {
    this.read = read;
  }

  @GetMapping("/payouts/{id}")
  public PayoutStatusWebResponse read(@PathVariable String id) {
    var result = read.read(id);
    var c = result.chain();
    return new PayoutStatusWebResponse(
        result.id(),
        result.status(),
        c == null
            ? null
            : new PayoutStatusWebResponse.ChainWebResponse(
                c.transactionHash(), c.nonce(), c.status(), c.confirmations()));
  }
}
