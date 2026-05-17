package id.ac.ui.cs.advprog.order.security;

import id.ac.ui.cs.advprog.order.dto.OrderRequest;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("orderAccessGuard")
@RequiredArgsConstructor
public class OrderAccessGuard {
    private final OrderRepository orderRepository;

    public boolean isOwner(Authentication authentication, String userId) {
        return authentication != null
                && authentication.getName() != null
                && authentication.getName().equals(userId);
    }

    public boolean isOwnerOfRequestedUser(Authentication authentication, String requestedUserId) {
        return isOwner(authentication, requestedUserId);
    }

    public boolean canCheckoutForRequestUser(Authentication authentication, OrderRequest orderRequest) {
        return orderRequest != null && isOwnerOfRequestedUser(authentication, orderRequest.getUserId());
    }

    public boolean canUpdateStatus(Authentication authentication, String orderId) {
        if (authentication == null || orderId == null || orderId.isBlank()) {
            return false;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        return order != null && isOwner(authentication, order.getJastiperId());
    }
}
