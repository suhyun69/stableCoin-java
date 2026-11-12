package com.example.demo.controller;

import com.example.demo.usecase.ReadChainInfoUseCase;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "stablepay.chain.enabled", havingValue = "true")
public class ChainHealthController {
  private final ReadChainInfoUseCase chain;

  public ChainHealthController(ReadChainInfoUseCase chain) {
    this.chain = chain;
  }

  @GetMapping("/dependencies/chain")
  public Map<String, Object> chain() {
    return Map.of("status", "reachable", "chainId", chain.chainId());
  }
}
