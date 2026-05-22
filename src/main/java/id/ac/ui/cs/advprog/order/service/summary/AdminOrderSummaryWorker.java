package id.ac.ui.cs.advprog.order.service.summary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminOrderSummaryWorker {
    private final AdminOrderSummaryMaterializer materializer;

    @Scheduled(fixedDelayString = "${order.summary.worker-delay-ms:3000}")
    public void refreshSummarySnapshot() {
        try {
            materializer.refresh();
        } catch (RuntimeException ex) {
            log.warn("admin_summary_refresh_failed reason={}", ex.getMessage(), ex);
        }
    }
}
