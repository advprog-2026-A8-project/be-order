package id.ac.ui.cs.advprog.order.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("orderAccessGuard")
public class OrderAccessGuard {

    public boolean isOwner(Authentication authentication, String userId) {
        return authentication != null
                && authentication.getName() != null
                && authentication.getName().equals(userId);
    }

    public boolean isOwnerOfRequestedUser(Authentication authentication, String requestedUserId) {
        return isOwner(authentication, requestedUserId);
    }
}
