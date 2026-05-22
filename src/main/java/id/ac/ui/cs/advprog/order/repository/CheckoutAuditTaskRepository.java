package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.enums.CheckoutAuditTaskStatus;
import id.ac.ui.cs.advprog.order.model.CheckoutAuditTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface CheckoutAuditTaskRepository extends JpaRepository<CheckoutAuditTask, Long> {
    List<CheckoutAuditTask> findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            Collection<CheckoutAuditTaskStatus> statuses,
            LocalDateTime now,
            Pageable pageable
    );
}
