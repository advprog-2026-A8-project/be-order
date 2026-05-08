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
                "Bearer " + normalizeTokenValue(internalToken),
                ADAPTER_NAME
        );
        String tokenValue = normalizedToken.substring("Bearer ".length());
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
        return token == null ? "" : token.trim();
    }
}
