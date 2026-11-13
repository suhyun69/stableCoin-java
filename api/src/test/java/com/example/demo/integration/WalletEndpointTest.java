package com.example.demo.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.demo.chain.adapter.out.rpc.*;
import com.example.demo.controller.WalletController;
import com.example.demo.usecase.impl.ReadWalletsUseCaseImpl;
import java.math.BigInteger;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.http.HttpService;

@Tag("chain")
class WalletEndpointTest {
  @Test
  void readsAnvilBalancesThroughEndpoint() throws Exception {
    var rpc =
        Web3j.build(
            new HttpService(
                System.getenv().getOrDefault("ANVIL_RPC_URL", "http://localhost:8545")));
    try {
      var yaml = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
      yaml.setResources(
          new org.springframework.core.io.FileSystemResource("src/main/resources/application.yml"));
      var resolver =
          new org.springframework.core.env.PropertySourcesPropertyResolver(
              new org.springframework.core.env.MutablePropertySources());
      String keys =
          resolver.resolveRequiredPlaceholders(
              yaml.getObject().getProperty("stablepay.chain.private-keys"));
      var wallets = new AnvilWalletRegistry(keys);
      var chain = new Web3jEthAdapter(rpc, wallets, 31337);
      chain.validateAnvilAccounts();
      org.junit.jupiter.api.Assertions.assertEquals(10, wallets.addresses().size());
      org.junit.jupiter.api.Assertions.assertEquals(
          new java.util.HashSet<>(rpc.ethAccounts().send().getAccounts()), wallets.addresses());
      var mvc =
          MockMvcBuilders.standaloneSetup(new WalletController(new ReadWalletsUseCaseImpl(chain)))
              .build();
      var result =
          mvc.perform(get("/wallets"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.wallets.length()").value(10))
              .andReturn();
      var json =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .readTree(result.getResponse().getContentAsString());
      var block = DefaultBlockParameter.valueOf(new BigInteger(json.get("blockNumber").asText()));
      java.util.Set<String> returned = new java.util.HashSet<>();
      for (var wallet : json.path("wallets")) {
        String address = wallet.get("address").asText();
        returned.add(address);
        org.junit.jupiter.api.Assertions.assertEquals(
            rpc.ethGetBalance(address, block).send().getBalance().toString(),
            wallet.get("balanceWei").asText());
      }
      org.junit.jupiter.api.Assertions.assertEquals(wallets.addresses(), returned);
      // Every configured account must be able to sign and send an actual payout.
      var addresses = new java.util.ArrayList<>(wallets.addresses());
      java.util.List<String> hashes = new java.util.ArrayList<>();
      for (int i = 0; i < addresses.size(); i++) {
        String from = addresses.get(i), to = addresses.get((i + 1) % addresses.size());
        var transfer =
            new com.example.demo.chain.domain.ChainTransaction(
                "ctx_test_" + i,
                "pay_test_" + i,
                from,
                to,
                BigInteger.ONE,
                chain.pendingNonce(from),
                "CREATED",
                null,
                null,
                null,
                0);
        var signed = chain.sign(transfer);
        var decoded =
            (org.web3j.crypto.SignedRawTransaction)
                org.web3j.crypto.TransactionDecoder.decode(signed.raw());
        org.junit.jupiter.api.Assertions.assertEquals(from, decoded.getFrom());
        org.junit.jupiter.api.Assertions.assertEquals(to, decoded.getTo());
        chain.broadcast(signed);
        hashes.add(signed.hash());
      }
      long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
      while (hashes.stream().anyMatch(h -> chain.receipt(h).isEmpty())
          && System.nanoTime() < deadline) Thread.sleep(100);
      for (String hash : hashes)
        org.junit.jupiter.api.Assertions.assertTrue(chain.receipt(hash).orElseThrow().successful());
    } finally {
      rpc.shutdown();
    }
  }
}
