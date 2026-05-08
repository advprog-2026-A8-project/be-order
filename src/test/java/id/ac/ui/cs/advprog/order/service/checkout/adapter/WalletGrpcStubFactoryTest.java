package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.bewallettransaksi.grpc.WalletContractServiceGrpc;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletGrpcStubFactoryTest {

    @Test
    void createStubShouldWorkWithBearerPrefixToken() {
        WalletGrpcStubFactory factory = new WalletGrpcStubFactory();
        ReflectionTestUtils.setField(factory, "grpcHost", "localhost");
        ReflectionTestUtils.setField(factory, "grpcPort", 9090);
        ReflectionTestUtils.setField(factory, "internalToken", "Bearer test-token");

        ReflectionTestUtils.invokeMethod(factory, "init");
        WalletContractServiceGrpc.WalletContractServiceBlockingStub stub = factory.createStub();

        assertNotNull(stub);
        ReflectionTestUtils.invokeMethod(factory, "destroy");
    }

    @Test
    void createStubShouldWorkWithRawTokenAndTrimSpaces() {
        WalletGrpcStubFactory factory = new WalletGrpcStubFactory();
        ReflectionTestUtils.setField(factory, "grpcHost", "localhost");
        ReflectionTestUtils.setField(factory, "grpcPort", 9090);
        ReflectionTestUtils.setField(factory, "internalToken", "  raw-token  ");

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
}
