package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByUserId(String userId);
    List<Order> findByUserIdAndStatusIn(String userId, Collection<OrderStatus> statuses);
    List<Order> findByJastiperIdAndStatusIn(String jastiperId, Collection<OrderStatus> statuses);
    List<Order> findByStatusIn(Collection<OrderStatus> statuses);
    Page<Order> findByStatusIn(Collection<OrderStatus> statuses, Pageable pageable);
}
