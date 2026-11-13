package com.example.demo.controller;

import com.example.demo.controller.dto.*;
import com.example.demo.usecase.CreatePayoutUseCase;
import com.example.demo.usecase.dto.CreatePayoutAppRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payouts")
public class PayoutController {
  private final CreatePayoutUseCase create;

  public PayoutController(CreatePayoutUseCase create) {
    this.create = create;
  }

  @PostMapping
  public ResponseEntity<PayoutWebResponse> create(
      @Valid @RequestBody CreatePayoutWebRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var result =
        create.create(
            new CreatePayoutAppRequest(
                request.merchantId(), request.from(), request.to(), request.amount(), key));
    return ResponseEntity.accepted()
        .location(URI.create("/payouts/" + result.id()))
        .body(
            new PayoutWebResponse(
                result.id(),
                result.merchantId(),
                result.from(),
                result.to(),
                result.amount(),
                result.status()));
  }
}
