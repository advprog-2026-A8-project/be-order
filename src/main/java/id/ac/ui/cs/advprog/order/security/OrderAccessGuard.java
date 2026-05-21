package id.ac.ui.cs.advprog.order.security;

import id.ac.ui.cs.advprog.order.dto.OrderRequest;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component("orderAccessGuard")
@RequiredArgsConstructor
public class OrderAccessGuard {
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ID = "id";

    private final OrderRepository orderRepository;

    public boolean isOwner(Authentication authentication, String userId) {
        if (authentication == null || userId == null || userId.isBlank()) {
            return false;
        }
        Set<String> identityCandidates = extractIdentityCandidates(authentication);
        return identityCandidates.contains(userId.trim());
    }

    public boolean isOwnerOfRequestedUser(Authentication authentication, String requestedUserId) {
        return isOwner(authentication, requestedUserId);
    }

    public boolean canCheckoutForRequestUser(Authentication authentication, OrderRequest orderRequest) {
        return orderRequest != null && isOwnerOfRequestedUser(authentication, orderRequest.getUserId());
    }

    public String resolveCheckoutUserId(Authentication authentication, String requestedUserId) {
        if (requestedUserId == null || requestedUserId.isBlank()) {
            throw new IllegalArgumentException("User ID request tidak boleh kosong");
        }
        if (!isOwner(authentication, requestedUserId)) {
            throw new IllegalArgumentException("User tidak berhak melakukan checkout untuk userId tersebut");
        }

        String normalizedRequested = requestedUserId.trim();
        Set<String> identityCandidates = extractIdentityCandidates(authentication);
        if (identityCandidates.contains(normalizedRequested)) {
            return normalizedRequested;
        }

        for (String candidate : identityCandidates) {
            if (looksLikeUuid(candidate)) {
                return candidate;
            }
        }
        return normalizedRequested;
    }

    public boolean canUpdateStatus(Authentication authentication, String orderId) {
        if (authentication == null || orderId == null || orderId.isBlank()) {
            return false;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        return order != null && isOwner(authentication, order.getJastiperId());
    }

    public boolean canReadOrder(Authentication authentication, String orderId) {
        if (authentication == null || orderId == null || orderId.isBlank()) {
            return false;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return false;
        }
        return isOwner(authentication, order.getUserId()) || isOwner(authentication, order.getJastiperId());
    }

    private Set<String> extractIdentityCandidates(Authentication authentication) {
        Set<String> identities = new LinkedHashSet<>();
        addIfPresent(identities, authentication.getName());

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            addIfPresent(identities, jwtAuth.getToken().getSubject());
            addIfPresent(identities, jwtAuth.getToken().getClaimAsString(CLAIM_EMAIL));
            addIfPresent(identities, jwtAuth.getToken().getClaimAsString(CLAIM_USER_ID));
            addIfPresent(identities, jwtAuth.getToken().getClaimAsString(CLAIM_ID));
        }
        return identities;
    }

    private void addIfPresent(Set<String> identities, String candidate) {
        if (candidate == null) {
            return;
        }
        String normalized = candidate.trim();
        if (!normalized.isEmpty()) {
            identities.add(normalized);
        }
    }

    private boolean looksLikeUuid(String candidate) {
        try {
            UUID.fromString(candidate);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
