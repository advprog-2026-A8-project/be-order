package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.enums.RatingSyncStatus;
import id.ac.ui.cs.advprog.order.model.RatingSyncTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RatingSyncTaskRepository extends JpaRepository<RatingSyncTask, Long> {
    Optional<RatingSyncTask> findByOrderId(String orderId);

    List<RatingSyncTask> findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            Collection<RatingSyncStatus> statuses,
            LocalDateTime now,
            Pageable pageable
    );
}

