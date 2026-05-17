package id.ac.ui.cs.advprog.order.security;

import id.ac.ui.cs.advprog.order.dto.OrderRequest;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAccessGuardTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderAccessGuard guard;

    @Test
    void isOwnerShouldReturnTrueWhenAuthenticationNameMatchesUserId() {
        Authentication auth = new TestingAuthenticationToken("user-1", "n/a");
        assertTrue(guard.isOwner(auth, "user-1"));
    }

    @Test
    void isOwnerShouldReturnFalseWhenAuthenticationNameDiffers() {
        Authentication auth = new TestingAuthenticationToken("user-1", "n/a");
        assertFalse(guard.isOwner(auth, "user-2"));
    }

    @Test
    void isOwnerOfRequestedUserShouldDelegateToIsOwner() {
        Authentication auth = new TestingAuthenticationToken("user-1", "n/a");
        assertTrue(guard.isOwnerOfRequestedUser(auth, "user-1"));
        assertFalse(guard.isOwnerOfRequestedUser(auth, "user-2"));
    }

    @Test
    void canCheckoutForRequestUserShouldValidateOwnershipAndRequestPresence() {
        Authentication auth = new TestingAuthenticationToken("user-1", "n/a");
        OrderRequest request = new OrderRequest();
        request.setUserId("user-1");

        assertTrue(guard.canCheckoutForRequestUser(auth, request));

        request.setUserId("user-2");
        assertFalse(guard.canCheckoutForRequestUser(auth, request));
        assertFalse(guard.canCheckoutForRequestUser(auth, null));
    }

    @Test
    void canUpdateStatusShouldReturnTrueForOwnerJastiper() {
        Authentication auth = new TestingAuthenticationToken("jastiper-1", "n/a");
        Order order = new Order();
        order.setJastiperId("jastiper-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertTrue(guard.canUpdateStatus(auth, "order-1"));
    }

    @Test
    void canUpdateStatusShouldReturnFalseForNonOwnerOrMissingOrder() {
        Authentication auth = new TestingAuthenticationToken("jastiper-2", "n/a");
        Order order = new Order();
        order.setJastiperId("jastiper-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertFalse(guard.canUpdateStatus(auth, "order-1"));
        assertFalse(guard.canUpdateStatus(auth, "missing"));
        assertFalse(guard.canUpdateStatus(auth, " "));
        assertFalse(guard.canUpdateStatus(null, "order-1"));
    }

    @Test
    void isOwnerShouldUseJwtUserIdClaimWhenAvailable() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", "user@example.com", "userId", "550e8400-e29b-41d4-a716-446655440000")
        );
        Authentication auth = new JwtAuthenticationToken(jwt);

        assertTrue(guard.isOwner(auth, "550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void canReadOrderShouldReturnTrueForTitiperOwnerOrJastiperOwner() {
        Order order = new Order();
        order.setUserId("titiper-1");
        order.setJastiperId("jastiper-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertTrue(guard.canReadOrder(new TestingAuthenticationToken("titiper-1", "n/a"), "order-1"));
        assertTrue(guard.canReadOrder(new TestingAuthenticationToken("jastiper-1", "n/a"), "order-1"));
    }

    @Test
    void canReadOrderShouldReturnFalseForNonOwnerOrInvalidInput() {
        Order order = new Order();
        order.setUserId("titiper-1");
        order.setJastiperId("jastiper-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertFalse(guard.canReadOrder(new TestingAuthenticationToken("other", "n/a"), "order-1"));
        assertFalse(guard.canReadOrder(new TestingAuthenticationToken("other", "n/a"), "missing"));
        assertFalse(guard.canReadOrder(new TestingAuthenticationToken("other", "n/a"), " "));
        assertFalse(guard.canReadOrder(null, "order-1"));
    }
}
