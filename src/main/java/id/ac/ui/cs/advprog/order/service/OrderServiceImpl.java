package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;

    @Value("${order.inventory.url}")
    private String inventoryUrl;

    @Value("${order.wallet.url}")
    private String walletUrl;

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
            throw new IllegalStateException("Gagal mengurangi stok inventory, padahal saldo sudah terpotong. Hubungi Admin.");
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
                order.setStatus(newStatus);
                return orderRepository.save(order);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
        }
        return null;
    }
}