package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.bewallettransaksi.grpc.CheckBalanceResponse;
import id.ac.ui.cs.advprog.bewallettransaksi.grpc.WalletContractServiceGrpc;
import id.ac.ui.cs.advprog.bewallettransaksi.grpc.WalletMutationResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletRestAdapterTest {

    @Mock
    private WalletGrpcStubFactory walletGrpcStubFactory;

    @Mock
    private WalletContractServiceGrpc.WalletContractServiceBlockingStub stub;

    @InjectMocks
    private WalletRestAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "maxAttempts", 2);
    }

    @Test
    void ensureSufficientBalanceSuccess() {
        when(walletGrpcStubFactory.createStub()).thenReturn(stub);
        when(stub.checkBalance(any()))
                .thenReturn(CheckBalanceResponse.newBuilder().setSufficient(true).setCurrentBalance("1000").build());

        adapter.ensureSufficientBalance("00000000-0000-0000-0000-000000000001", 10000.0);
        verify(stub).checkBalance(any());
    }

    @Test
    void ensureSufficientBalanceShouldThrowWhenInsufficient() {
        when(walletGrpcStubFactory.createStub()).thenReturn(stub);
        when(stub.checkBalance(any()))
                .thenReturn(CheckBalanceResponse.newBuilder().setSufficient(false).setCurrentBalance("0").build());

        assertThrows(IllegalArgumentException.class,
                () -> adapter.ensureSufficientBalance("00000000-0000-0000-0000-000000000001", 10000.0));
    }

    @Test
    void debitSuccess() {
        when(walletGrpcStubFactory.createStub()).thenReturn(stub);
        when(stub.deductBalance(any()))
                .thenReturn(WalletMutationResponse.newBuilder().setSuccess(true).build());

        adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1");
        verify(stub).deductBalance(any());
    }

    @Test
    void refundSuccess() {
        when(walletGrpcStubFactory.createStub()).thenReturn(stub);
        when(stub.refundBalance(any()))
                .thenReturn(WalletMutationResponse.newBuilder().setSuccess(true).build());

        adapter.refund("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-r1");
        verify(stub).refundBalance(any());
    }

    @Test
    void debitShouldRetryOnRetryableContractError() {
        when(walletGrpcStubFactory.createStub()).thenReturn(stub);
        when(stub.deductBalance(any()))
                .thenReturn(WalletMutationResponse.newBuilder().setSuccess(false).setRetryable(true).build())
                .thenReturn(WalletMutationResponse.newBuilder().setSuccess(true).build());

        adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1");
        verify(stub, times(2)).deductBalance(any());
    }

    @Test
    void debitShouldThrowWhenRetryableErrorExhausted() {
        when(walletGrpcStubFactory.createStub()).thenReturn(stub);
        when(stub.deductBalance(any()))
                .thenReturn(WalletMutationResponse.newBuilder().setSuccess(false).setRetryable(true).build())
                .thenReturn(WalletMutationResponse.newBuilder().setSuccess(false).setRetryable(true).build());

        assertThrows(IllegalStateException.class, () ->
                adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1"));
    }

    @Test
    void debitShouldThrowWhenUserIdIsNotUuid() {
        assertThrows(IllegalArgumentException.class, () ->
                adapter.debit("not-uuid", "order-1", 10000.0, "idem-1"));
        verify(stub, never()).deductBalance(any());
    }

    @Test
    void ensureSufficientBalanceShouldThrowWhenUserIdIsNotUuid() {
        assertThrows(IllegalArgumentException.class, () ->
                adapter.ensureSufficientBalance("not-uuid", 10000.0));
        verify(stub, never()).checkBalance(any());
    }

    @Test
    void debitShouldFailFastWhenMaxAttemptsIsNotPositive() {
        ReflectionTestUtils.setField(adapter, "maxAttempts", 0);
        assertThrows(IllegalStateException.class, () ->
                adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1"));
    }

    @Test
    void debitShouldThrowOnInvalidArgumentStatus() {
        when(walletGrpcStubFactory.createStub()).thenReturn(stub);
        when(stub.deductBalance(any()))
                .thenThrow(new StatusRuntimeException(Status.INVALID_ARGUMENT));

        assertThrows(IllegalArgumentException.class, () ->
                adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1"));
    }

    @Test
    void refundShouldRetryOnUnavailableStatus() {
        when(walletGrpcStubFactory.createStub()).thenReturn(stub);
        when(stub.refundBalance(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                .thenReturn(WalletMutationResponse.newBuilder().setSuccess(true).build());

        adapter.refund("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-r1");
        verify(stub, times(2)).refundBalance(any());
    }
}
