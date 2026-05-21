package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.service.common.AdapterConfigValidator;
import id.ac.ui.cs.advprog.bewallettransaksi.grpc.WalletContractServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WalletGrpcStubFactory {
    private static final String ADAPTER_NAME = "Wallet-gRPC";
    private static final String HEADER_SERVICE_TOKEN = "x-service-token";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${order.wallet.grpc.host:localhost}")
    private String grpcHost;

    @Value("${order.wallet.grpc.port:9090}")
    private int grpcPort;

    @Value("${order.wallet.grpc.internal-token:}")
    private String internalToken;

    private ManagedChannel managedChannel;

    @PostConstruct
    void init() {
        managedChannel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
                .usePlaintext()
                .build();
    }

    public WalletContractServiceGrpc.WalletContractServiceBlockingStub createStub() {
        String normalizedToken = AdapterConfigValidator.validateAndNormalizeBearerToken(
                BEARER_PREFIX + normalizeTokenValue(internalToken),
                ADAPTER_NAME
        );
        String tokenValue = normalizedToken.substring(BEARER_PREFIX.length());
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of(HEADER_SERVICE_TOKEN, Metadata.ASCII_STRING_MARSHALLER), tokenValue);

        return WalletContractServiceGrpc.newBlockingStub(managedChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @PreDestroy
    void destroy() {
        if (managedChannel != null) {
            managedChannel.shutdown();
        }
    }

    private String normalizeTokenValue(String token) {
        if (token == null) {
            return "";
        }

        String normalized = token.trim();
        if (normalized.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            normalized = normalized.substring(BEARER_PREFIX.length()).trim();
        }

        if (hasWrappingQuote(normalized)) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        return normalized;
    }

    private boolean hasWrappingQuote(String token) {
        return token.length() >= 2
                && ((token.startsWith("\"") && token.endsWith("\""))
                || (token.startsWith("'") && token.endsWith("'")));
    }
}
