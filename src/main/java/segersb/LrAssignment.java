package segersb;

import io.reactivex.disposables.Disposable;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.websocket.WebSocketService;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ## High-level: Output all **ERC-721** transfers in your console.
 * <p>
 * The purpose of this assignment is to create a tool that output all **ERC-721** Transfers events in your console.
 * <p>
 * You need to create a tool to listen to new blocks on Ethereum. Within transactions, included into received blocks, filter out ones, that constrain ERC-721 Transfer event. That can be done with help of topic filter.
 * <p>
 * Once you have identified a transaction containing a transfer, fetch the associated contract information (name and symbol, tokenURI).
 * Print information like contract, tx hash, from (previous owner), to (next owner), tokenURI, symbol and name of the collection.
 * <p>
 * Bonus: Query the `tokenURI` (if presented) to get token metadata. Fetch the associated link, and display everything in the console.
 * <p>
 * Hints:
 * - You can get a free WSS endpoint at infura.io
 * - Topic to match Transfer : `0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef`
 * - [Web3j](https://docs.web3j.io/)
 * <p>
 * ---
 * <p>
 * Note: **This is not disguised work**, LR will not use the outcome of this technical assessment for any other purpose than evaluating your skills.
 */
public class LrAssignment {
    private static String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    private static String ENS_ADDRESS = "0x57f1887a8bf19b14fc0df6fd9b2acc9af147ea85";

    public static void main(String[] args) throws Exception {
        System.out.println("Staring assignment");

        System.out.print("Connecting to websocket... ");
        WebSocketService wss = new WebSocketService("wss://mainnet.infura.io/ws/v3/1fc9807e86174831a829078c92f1a692", false);
        wss.connect();
        System.out.println("done");

        Web3j web3j = Web3j.build(wss);
        EthFilter filter = new EthFilter().addSingleTopic(TRANSFER_TOPIC);

        System.out.println("Listening...");
        System.out.println();

        Disposable subscribe = web3j
                .ethLogFlowable(filter)
                .filter(LrAssignment::isErc721)
                .subscribe(log -> {
                    List<String> topics = log.getTopics();

                    String transactionHash = log.getTransactionHash();
                    String contractAddress = log.getAddress();
                    String fromAddress = topics.get(1);
                    String toAddress = topics.get(2);
                    BigInteger tokenId = new BigInteger(topics.get(3).substring(2), 16);

                    String contractName = getContractInfo(web3j, contractAddress, "name");
                    String contractSymbol = getContractInfo(web3j, contractAddress, "symbol");
                    String tokenURI = getTokenURI(web3j, contractAddress, tokenId);
                    String tokenMetaData = fetchMetaData(tokenURI);

                    System.out.println("ERC 721 transfer found");
                    System.out.println("contractAddress = " + contractAddress);
                    System.out.println("transactionHash = " + transactionHash);
                    System.out.println("fromAddress = " + fromAddress);
                    System.out.println("toAddress = " + toAddress);
                    System.out.println("tokenURI = " + tokenURI);
                    System.out.println("contractName = " + contractName);
                    System.out.println("contractSymbol = " + contractSymbol);
                    System.out.println("tokenMetaData = " + tokenMetaData);
                    System.out.println();
                }, error -> {
                    System.out.println("Error while checking for ERC 721: " + error.getMessage());
                    System.out.println();
                });

        Runtime.getRuntime().addShutdownHook(new Thread(subscribe::dispose));
    }

    static boolean isErc721(Log log) {
        if (log.getAddress().equals(ENS_ADDRESS)) {
            return false;
        }

        List<String> topics = log.getTopics();
        return topics.size() == 4 && topics.get(0).equals(TRANSFER_TOPIC);
    }

    static String getContractInfo(Web3j web3j, String contractAddress, String info) throws IOException {
        Function function = new Function(
                info,
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Utf8String>() {
                }));

        return callContractFunction(web3j, contractAddress, function);
    }

    static String getTokenURI(Web3j web3j, String contractAddress, BigInteger tokenId) throws IOException {
        Function function = new Function(
                "tokenURI",
                Arrays.asList(new Uint256(tokenId)),
                Collections.singletonList(new TypeReference<Utf8String>() {
                }));

        return callContractFunction(web3j, contractAddress, function);
    }

    static String callContractFunction(Web3j web3j, String contractAddress, Function function) throws IOException {
        String encodedFunction = FunctionEncoder.encode(function);
        Transaction transaction = Transaction.createEthCallTransaction(contractAddress, contractAddress, encodedFunction);
        EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();

        List<Type> responseTypes = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (responseTypes.size() > 0) {
            return responseTypes.get(0).getValue().toString();
        }
        return "";
    }

    static String fetchMetaData(String tokenURI) throws Exception {
        URI uri = null;
        if (tokenURI.startsWith("http")) {
            uri = URI.create(tokenURI);
        } else if (tokenURI.startsWith("ipfs://")) {
            uri = URI.create("https://ipfs.io/ipfs/" + tokenURI.replace("ipfs://", ""));
        }

        if (uri != null) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("accept", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }
        return "";
    }
}
