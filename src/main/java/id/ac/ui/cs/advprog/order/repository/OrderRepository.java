package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
}