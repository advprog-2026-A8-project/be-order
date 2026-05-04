package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.model.OrderIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderIdempotencyRepository extends JpaRepository<OrderIdempotency, String> {
}
