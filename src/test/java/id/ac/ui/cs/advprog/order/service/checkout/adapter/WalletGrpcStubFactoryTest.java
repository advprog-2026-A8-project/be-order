package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.bewallettransaksi.grpc.WalletContractServiceGrpc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletGrpcStubFactoryTest {

    @ParameterizedTest
    @MethodSource("validInternalTokenCases")
    void createStubShouldWorkWithVariousValidTokenFormats(String token) {
        WalletGrpcStubFactory factory = new WalletGrpcStubFactory();
        ReflectionTestUtils.setField(factory, "grpcHost", "localhost");
        ReflectionTestUtils.setField(factory, "grpcPort", 9090);
        ReflectionTestUtils.setField(factory, "internalToken", token);

        ReflectionTestUtils.invokeMethod(factory, "init");
        WalletContractServiceGrpc.WalletContractServiceBlockingStub stub = factory.createStub();

        assertNotNull(stub);
        ReflectionTestUtils.invokeMethod(factory, "destroy");
    }

    @Test
    void createStubShouldThrowWhenTokenMissing() {
        WalletGrpcStubFactory factory = new WalletGrpcStubFactory();
        ReflectionTestUtils.setField(factory, "grpcHost", "localhost");
        ReflectionTestUtils.setField(factory, "grpcPort", 9090);
        ReflectionTestUtils.setField(factory, "internalToken", "   ");

        ReflectionTestUtils.invokeMethod(factory, "init");
        assertThrows(IllegalStateException.class, factory::createStub);
        ReflectionTestUtils.invokeMethod(factory, "destroy");
    }

    @Test
    void destroyShouldBeSafeWhenChannelNotInitialized() {
        WalletGrpcStubFactory factory = new WalletGrpcStubFactory();
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(factory, "destroy"));
    }

    private static Stream<String> validInternalTokenCases() {
        return Stream.of(
                "Bearer test-token",
                "  raw-token  ",
                "\"quoted-token\"",
                "Bearer \"quoted-token\"",
                "bearer 'quoted-token'",
                "\"half-quoted-token"
        );
    }
}
