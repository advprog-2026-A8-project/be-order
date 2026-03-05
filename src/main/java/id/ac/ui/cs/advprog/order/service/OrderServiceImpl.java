package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestTemplate restTemplate;

    private final String INVENTORY_URL = "http://localhost:8081/api/products";
    private final String WALLET_URL = "http://localhost:8082/api/wallets";

    @Override
    public Order createOrder(Order order) {
        String productUrl = INVENTORY_URL + "/" + order.getProductId();
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

        String walletUrl = WALLET_URL + "/" + order.getUserId() + "/debit?amount=" + totalPrice;

        try {
            restTemplate.put(walletUrl, null);
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("Saldo Wallet tidak mencukupi atau User tidak ditemukan!");
        }

        String reduceStockUrl = INVENTORY_URL + "/" + order.getProductId() + "/reduce-stock?quantity=" + order.getJumlah();

        try {
            restTemplate.put(reduceStockUrl, null);
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Gagal mengurangi stok inventory, padahal saldo sudah terpotong. Hubungi Admin.");
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