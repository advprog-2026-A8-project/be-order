package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(@RequestBody Order orderRequest) {
        orderRequest.setStatus(OrderStatus.PENDING);
        // orderRepository.save(orderRequest);
        return ResponseEntity.ok(orderRequest);
    }
}