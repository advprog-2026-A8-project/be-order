package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.enums.CompensationTaskStatus;
import id.ac.ui.cs.advprog.order.model.OrderCompensationTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderCompensationTaskRepository extends JpaRepository<OrderCompensationTask, Long> {
    Optional<OrderCompensationTask> findByTaskKey(String taskKey);

    List<OrderCompensationTask> findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            Collection<CompensationTaskStatus> statuses,
            LocalDateTime now,
            Pageable pageable
    );
}

