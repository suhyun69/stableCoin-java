package com.example.demo.chain.adapter.out.rpc;
import com.example.demo.chain.application.port.out.EthChainPort;
import com.example.demo.chain.domain.*;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.*;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.utils.Numeric;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Callable;

public class Web3jEthAdapter implements EthChainPort {
    private final Web3j rpc;
    private final Credentials credentials;
    private final long expectedChain;
    public Web3jEthAdapter(Web3j rpc, String privateKey, long chainId) {
        this.rpc=rpc; this.credentials=Credentials.create(privateKey); this.expectedChain=chainId;
    }
    private <T extends Response<?>> T request(Callable<T> request) {
        try {
            T response=request.call();
            if(response.hasError()) throw new IllegalStateException("chain RPC rejected request: "+response.getError().getMessage());
            return response;
        } catch(RuntimeException e) { throw e; }
        catch(Exception e) { throw new IllegalStateException("chain RPC unavailable",e); }
    }
    public long chainId() {
        long observed=request(() -> rpc.ethChainId().send()).getChainId().longValueExact();
        if(observed!=expectedChain) throw new IllegalStateException("chain ID mismatch");
        return observed;
    }
    public String sender() { return credentials.getAddress().toLowerCase(Locale.ROOT); }
    public long pendingNonce() {
        chainId(); return request(() -> rpc.ethGetTransactionCount(sender(),DefaultBlockParameterName.PENDING).send()).getTransactionCount().longValueExact();
    }
    public SignedEthTransaction sign(ChainTransaction transaction) {
        chainId();
        if(!sender().equalsIgnoreCase(transaction.sender())) throw new IllegalArgumentException("signer address mismatch");
        BigInteger gasPrice=request(() -> rpc.ethGasPrice().send()).getGasPrice().multiply(BigInteger.TWO);
        var estimateRequest=new org.web3j.protocol.core.methods.request.Transaction(sender(),BigInteger.valueOf(transaction.nonce()),gasPrice,null,transaction.recipient(),transaction.amount(),"0x");
        BigInteger gas=request(() -> rpc.ethEstimateGas(estimateRequest).send()).getAmountUsed().multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
        if(gas.compareTo(BigInteger.valueOf(1_000_000))>0) throw new IllegalArgumentException("gas estimate exceeds local payout limit");
        var raw=RawTransaction.createEtherTransaction(BigInteger.valueOf(transaction.nonce()),gasPrice,gas,transaction.recipient(),transaction.amount());
        byte[] signed=TransactionEncoder.signMessage(raw,expectedChain,credentials);
        return new SignedEthTransaction(Numeric.toHexString(signed),Hash.sha3(Numeric.toHexString(signed)));
    }
    public void broadcast(SignedEthTransaction transaction) {
        chainId();
        var response=request(() -> rpc.ethSendRawTransaction(transaction.raw()).send());
        if(!transaction.hash().equalsIgnoreCase(response.getTransactionHash())) throw new IllegalStateException("broadcast hash mismatch");
    }
    public Optional<EthTransfer> transaction(String hash) {
        chainId(); return request(() -> rpc.ethGetTransactionByHash(hash).send()).getTransaction().map(this::transfer);
    }
    private EthTransfer transfer(Transaction tx) {
        return new EthTransfer(tx.getHash(),tx.getFrom().toLowerCase(Locale.ROOT),tx.getTo()==null?null:tx.getTo().toLowerCase(Locale.ROOT),
            tx.getValue(),tx.getNonce().longValueExact(),tx.getBlockNumberRaw()==null?null:tx.getBlockNumber().longValueExact(),tx.getBlockHash());
    }
    public Optional<EthReceipt> receipt(String hash) {
        chainId(); return request(() -> rpc.ethGetTransactionReceipt(hash).send()).getTransactionReceipt().map(
            r -> new EthReceipt(r.getTransactionHash(),r.isStatusOK(),r.getBlockNumber().longValueExact(),r.getBlockHash()));
    }
    public long head() { chainId(); return request(() -> rpc.ethBlockNumber().send()).getBlockNumber().longValueExact(); }
    public String blockHash(long number) {
        var block=request(() -> rpc.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(number)),false).send()).getBlock();
        if(block==null) throw new IllegalStateException("block unavailable");
        return block.getHash();
    }
    public List<EthTransfer> transfers(long from,long to) {
        chainId();
        if(from<0 || to<from || to-from>=1000) throw new IllegalArgumentException("block range must contain 1 to 1000 blocks");
        List<EthTransfer> result=new ArrayList<>();
        for(long number=from;number<=to;number++) {
            long current=number;
            var block=request(() -> rpc.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(current)),true).send()).getBlock();
            if(block==null) throw new IllegalStateException("block unavailable");
            for(var item:block.getTransactions()) {
                var transfer=transfer((Transaction)item.get());
                if(transfer.value().signum()>0) result.add(transfer);
            }
        }
        return result;
    }
}
