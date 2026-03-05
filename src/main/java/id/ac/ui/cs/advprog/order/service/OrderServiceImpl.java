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
        return null;
    }

    @Override
    public List<Order> findAllOrders() {
        return null;
    }

    @Override
    public Order findOrderById(String id) {
        return null;
    }

    @Override
    public Order updateOrderStatus(String id, String status) {
        return null;
    }
}