package id.ac.ui.cs.advprog.order.service.rating.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfileRestAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ProfileRestAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "profileUrl", "http://localhost:8083/api/profile");
    }

    @Test
    void submitRatingSuccess() {
        adapter.submitRating("o1", "u1", "j1", "p1", 5, 4);
        verify(restTemplate).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void submitRatingFailureShouldThrow() {
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "u1", "j1", "p1", 5, 4));
    }
}
