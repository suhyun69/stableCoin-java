package com.example.demo.integration;
import com.example.demo.chain.adapter.out.rpc.Web3jEthAdapter;
import com.example.demo.chain.domain.*;
import org.junit.jupiter.api.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.crypto.*;
import java.math.BigInteger;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
@Tag("chain")
class EthRpcTest {
    public static final String KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    @Test void signsBroadcastsAndVerifiesNativeEthTransfer() throws Exception {
        var rpc=Web3j.build(new HttpService(System.getenv().getOrDefault("ANVIL_RPC_URL","http://localhost:8545")));
        try {
            var chain=new Web3jEthAdapter(rpc,KEY,31337);
            assertEquals(31337,chain.chainId());
            long nonce=chain.pendingNonce();
            String recipient="0x"+"ab".repeat(20);
            var transaction=new ChainTransaction("ctx_test","pay_test",chain.sender(),recipient,BigInteger.ONE,nonce,"CREATED",null,null,null,0);
            var signed=chain.sign(transaction);
            var decoded=(SignedRawTransaction)TransactionDecoder.decode(signed.raw());
            assertEquals(chain.sender(),decoded.getFrom());
            assertEquals(BigInteger.ONE,decoded.getValue());
            assertEquals(recipient,decoded.getTo());
            chain.broadcast(signed);
            long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
            while(chain.receipt(signed.hash()).isEmpty() && System.nanoTime()<deadline) Thread.sleep(100);
            var receipt=chain.receipt(signed.hash()).orElseThrow();
            assertTrue(receipt.successful());
            assertEquals(signed.hash(),chain.transaction(signed.hash()).orElseThrow().hash());
            assertEquals(receipt.blockHash(),chain.blockHash(receipt.blockNumber()));
            assertTrue(chain.transfers(receipt.blockNumber(),receipt.blockNumber()).stream().anyMatch(t -> t.hash().equals(signed.hash())));
            assertThrows(IllegalStateException.class,() -> new Web3jEthAdapter(rpc,KEY,1).chainId());
        } finally {rpc.shutdown();}
    }
}
