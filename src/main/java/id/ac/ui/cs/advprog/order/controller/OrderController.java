package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.dto.OrderRequest;
import id.ac.ui.cs.advprog.order.dto.RatingRequest;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(
            @Valid @RequestBody OrderRequest orderRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        Order order = new Order();
        order.setProductId(orderRequest.getProductId());
        order.setUserId(orderRequest.getUserId());
        order.setJastiperId(orderRequest.getJastiperId());
        order.setJumlah(orderRequest.getJumlah());
        order.setAlamatPengiriman(orderRequest.getAlamatPengiriman());
        order.setStatus(OrderStatus.PENDING);

        Order savedOrder = orderService.createOrder(order, normalizedIdempotencyKey);
        return ResponseEntity.ok(savedOrder);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }

        String trimmed = idempotencyKey.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Idempotency-Key tidak boleh kosong");
        }
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key melebihi panjang maksimum 128 karakter");
        }
        return trimmed;
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        List<Order> orders = orderService.findAllOrders();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable String id) {
        Order order = orderService.findOrderById(id);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable String id, @RequestParam String status) {
        try {
            Order updatedOrder = orderService.updateOrderStatus(id, status);
            if (updatedOrder == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(updatedOrder);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelByJastiper(@PathVariable String id, @RequestParam String jastiperId) {
        try {
            Order cancelledOrder = orderService.cancelOrderByJastiper(id, jastiperId);
            if (cancelledOrder == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(cancelledOrder);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/titiper/{userId}/active")
    public ResponseEntity<List<Order>> getTitiperActiveOrders(@PathVariable String userId) {
        return ResponseEntity.ok(orderService.findTitiperActiveOrders(userId));
    }

    @GetMapping("/titiper/{userId}/history")
    public ResponseEntity<List<Order>> getTitiperOrderHistory(@PathVariable String userId) {
        return ResponseEntity.ok(orderService.findTitiperOrderHistory(userId));
    }

    @GetMapping("/jastiper/{jastiperId}/todo")
    public ResponseEntity<List<Order>> getJastiperTodoOrders(@PathVariable String jastiperId) {
        return ResponseEntity.ok(orderService.findJastiperTodoOrders(jastiperId));
    }

    @GetMapping("/jastiper/{jastiperId}/processing")
    public ResponseEntity<List<Order>> getJastiperProcessingOrders(@PathVariable String jastiperId) {
        return ResponseEntity.ok(orderService.findJastiperProcessingOrders(jastiperId));
    }

    @GetMapping("/jastiper/{jastiperId}/completed")
    public ResponseEntity<List<Order>> getJastiperCompletedOrders(@PathVariable String jastiperId) {
        return ResponseEntity.ok(orderService.findJastiperCompletedOrders(jastiperId));
    }

    @GetMapping("/admin/active")
    public ResponseEntity<List<Order>> getAdminActiveOrders() {
        return ResponseEntity.ok(orderService.findAdminActiveOrders());
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<Order> submitRating(@PathVariable String id, @Valid @RequestBody RatingRequest ratingRequest) {
        try {
            Order ratedOrder = orderService.submitOrderRating(
                    id,
                    ratingRequest.getUserId(),
                    ratingRequest.getJastiperRating(),
                    ratingRequest.getProductRating()
            );
            if (ratedOrder == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ratedOrder);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
