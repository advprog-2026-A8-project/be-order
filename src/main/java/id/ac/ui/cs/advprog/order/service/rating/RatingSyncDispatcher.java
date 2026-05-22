package id.ac.ui.cs.advprog.order.service.rating;

import id.ac.ui.cs.advprog.order.enums.RatingSyncStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.RatingSyncTask;
import id.ac.ui.cs.advprog.order.repository.RatingSyncTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RatingSyncDispatcher {
    private final RatingSyncTaskRepository ratingSyncTaskRepository;

    @Transactional
    public void enqueue(Order order) {
        RatingSyncTask task = ratingSyncTaskRepository.findByOrderId(order.getId())
                .orElseGet(RatingSyncTask::new);

        task.setOrderId(order.getId());
        task.setUserId(order.getUserId());
        task.setJastiperId(order.getJastiperId());
        task.setProductId(order.getProductId());
        task.setJastiperRating(order.getJastiperRating());
        task.setProductRating(order.getProductRating());
        task.setStatus(RatingSyncStatus.PENDING);
        task.setAttemptCount(0);
        task.setLastError(null);
        task.setNextRetryAt(LocalDateTime.now());
        ratingSyncTaskRepository.save(task);
    }
}

