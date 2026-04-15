package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;

    @Value("${order.inventory.url}")
    private String inventoryUrl;

    @Value("${order.wallet.url}")
    private String walletUrl;

    private static final Map<OrderStatus, EnumSet<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PAID, EnumSet.of(OrderStatus.PURCHASED, OrderStatus.CANCELLED),
            OrderStatus.PURCHASED, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED),
            OrderStatus.PENDING, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    @Override
    public Order createOrder(Order order) {
        if (order.getProductId() == null || order.getUserId() == null) {
            throw new IllegalArgumentException("Product ID dan User ID tidak boleh kosong");
        }

        String productUrl = UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(order.getProductId())
                .toUriString();

        InventoryResponse product;
        try {
            product = restTemplate.getForObject(productUrl, InventoryResponse.class);
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("Produk tidak ditemukan di Inventory!");
        }

        if (product == null || product.getProductQuantity() < order.getJumlah()) {
            throw new IllegalArgumentException("Stok barang tidak mencukupi!");
        }

        Double totalPrice = product.getPrice() * order.getJumlah();

        String userWalletUrl = UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment(order.getUserId(), "debit")
                .queryParam("amount", totalPrice)
                .toUriString();

        try {
            restTemplate.put(userWalletUrl, null);
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("Saldo Wallet tidak mencukupi atau User tidak ditemukan!");
        }

        String reduceStockUrl = UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(order.getProductId(), "reduce-stock")
                .queryParam("quantity", order.getJumlah())
                .toUriString();

        try {
            restTemplate.put(reduceStockUrl, null);
        } catch (HttpClientErrorException e) {
            String refundUrl = UriComponentsBuilder.fromUriString(walletUrl)
                    .pathSegment(order.getUserId(), "credit")
                    .queryParam("amount", totalPrice)
                    .toUriString();
            restTemplate.put(refundUrl, null);
            throw new IllegalStateException("Gagal mengurangi stok inventory, padahal saldo sudah terpotong. Dana direfund.");
        }

        order.setStatus(OrderStatus.PAID);
        return orderRepository.save(order);
    }

    @Override
    public List<Order> findAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    public Order findOrderById(String id) {
        return orderRepository.findById(id).orElse(null);
    }

    @Override
    public Order updateOrderStatus(String id, String status) {
        Order order = findOrderById(id);
        if (order != null) {
            try {
                OrderStatus newStatus = OrderStatus.valueOf(status.toUpperCase());
                if (!VALID_TRANSITIONS.getOrDefault(order.getStatus(), EnumSet.noneOf(OrderStatus.class))
                        .contains(newStatus)) {
                    throw new IllegalArgumentException("Invalid transition");
                }
                order.setStatus(newStatus);
                return orderRepository.save(order);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
        }
        return null;
    }
}
