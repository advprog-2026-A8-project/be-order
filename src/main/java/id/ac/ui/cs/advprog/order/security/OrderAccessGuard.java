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
import java.util.Optional;

@Component("orderAccessGuard")
@RequiredArgsConstructor
public class OrderAccessGuard {
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

        Optional<String> exactMatch = identityCandidates.stream()
                .filter(candidate -> candidate.equals(normalizedRequested))
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        Optional<String> preferredUuidStyleIdentity = identityCandidates.stream()
                .filter(candidate -> {
                    try {
                        java.util.UUID.fromString(candidate);
                        return true;
                    } catch (IllegalArgumentException ex) {
                        return false;
                    }
                })
                .findFirst();
        return preferredUuidStyleIdentity.orElse(normalizedRequested);
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
            addIfPresent(identities, jwtAuth.getToken().getClaimAsString("email"));
            addIfPresent(identities, jwtAuth.getToken().getClaimAsString("userId"));
            addIfPresent(identities, jwtAuth.getToken().getClaimAsString("id"));
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
}
