package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.model.AdminOrderSummarySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminOrderSummarySnapshotRepository extends JpaRepository<AdminOrderSummarySnapshot, Integer> {
}
