package id.ac.ui.cs.advprog.order.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderAccessGuardTest {

    private final OrderAccessGuard guard = new OrderAccessGuard();

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
}

