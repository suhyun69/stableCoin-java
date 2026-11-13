package com.example.demo.config;

import com.example.demo.chain.adapter.out.rpc.AnvilWalletRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class WalletConfig {
  @Bean
  AnvilWalletRegistry anvilWalletRegistry(@Value("${stablepay.chain.private-keys}") String keys) {
    return new AnvilWalletRegistry(keys);
  }
}
