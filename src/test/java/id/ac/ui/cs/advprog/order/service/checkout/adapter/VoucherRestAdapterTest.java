package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherRestAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private VoucherRestAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "voucherUrl", "http://localhost:7002/api/vouchers");
        ReflectionTestUtils.setField(adapter, "maxAttempts", 2);
    }

    @Test
    void validateDiscountSuccess() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("valid", true, "discountAmount", 2500.0));

        double discount = adapter.validateDiscount("HEMAT10", 10000.0);

        assertEquals(2500.0, discount);
    }

    @Test
    void validateDiscountShouldThrowWhenInvalid() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("valid", false));

        assertThrows(IllegalArgumentException.class, () -> adapter.validateDiscount("BAD", 10000.0));
    }

    @Test
    void validateDiscountShouldRetryOnTransientFailure() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenReturn(Map.of("valid", true, "discountAmount", 1000.0));

        double discount = adapter.validateDiscount("HEMAT10", 10000.0);

        assertEquals(1000.0, discount);
        verify(restTemplate, times(2)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    void validateDiscountShouldThrowWhenTransientFailureExhausted() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenThrow(new ResourceAccessException("timeout-2"));

        assertThrows(IllegalStateException.class, () -> adapter.validateDiscount("HEMAT10", 10000.0));
    }

    @Test
    void useVoucherSuccess() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("success", true));

        adapter.useVoucher("HEMAT10");

        verify(restTemplate).postForObject(eq("http://localhost:7002/api/vouchers/use"), any(), eq(Map.class));
    }

    @Test
    void useVoucherShouldThrowOn4xx() {
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(restTemplate).postForObject(anyString(), any(), eq(Map.class));

        assertThrows(IllegalArgumentException.class, () -> adapter.useVoucher("BAD"));
    }
}
