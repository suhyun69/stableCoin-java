package com.example.demo.usecase;

import com.example.demo.usecase.dto.*;

public interface CreatePayoutUseCase {
  PayoutAppResponse create(CreatePayoutAppRequest request);
}
