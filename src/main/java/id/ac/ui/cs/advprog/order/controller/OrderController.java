package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.dto.AdminOrderSummaryResponse;
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
    private static final String MESSAGE_IDEMPOTENCY_KEY_BLANK = "Idempotency-Key tidak boleh kosong";
    private static final String MESSAGE_IDEMPOTENCY_KEY_TOO_LONG =
            "Idempotency-Key melebihi panjang maksimum 128 karakter";

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
            throw new IllegalArgumentException(MESSAGE_IDEMPOTENCY_KEY_BLANK);
        }
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(MESSAGE_IDEMPOTENCY_KEY_TOO_LONG);
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
        Order updatedOrder = orderService.updateOrderStatus(id, status);
        return toOrderResponse(updatedOrder);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelByJastiper(@PathVariable String id, @RequestParam String jastiperId) {
        Order cancelledOrder = orderService.cancelOrderByJastiper(id, jastiperId);
        return toOrderResponse(cancelledOrder);
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

    @GetMapping("/admin/by-status")
    public ResponseEntity<List<Order>> getAdminOrdersByStatus(@RequestParam String status) {
        return ResponseEntity.ok(orderService.findAdminOrdersByStatus(status));
    }

    @GetMapping("/admin/summary")
    public ResponseEntity<AdminOrderSummaryResponse> getAdminOrderSummary() {
        return ResponseEntity.ok(orderService.getAdminOrderSummary());
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<Order> submitRating(@PathVariable String id, @Valid @RequestBody RatingRequest ratingRequest) {
        Order ratedOrder = orderService.submitOrderRating(
                id,
                ratingRequest.getUserId(),
                ratingRequest.getJastiperRating(),
                ratingRequest.getProductRating()
        );
        return toOrderResponse(ratedOrder);
    }

    private ResponseEntity<Order> toOrderResponse(Order order) {
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }
}
