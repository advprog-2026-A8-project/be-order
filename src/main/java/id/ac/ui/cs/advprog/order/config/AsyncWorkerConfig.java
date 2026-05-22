package id.ac.ui.cs.advprog.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncWorkerConfig {

    @Bean(name = "compensationTaskExecutor")
    public Executor compensationTaskExecutor(
            @Value("${order.compensation.parallelism:4}") int parallelism
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int size = Math.max(1, parallelism);
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(size * 100);
        executor.setThreadNamePrefix("compensation-worker-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "checkoutAuditTaskExecutor")
    public Executor checkoutAuditTaskExecutor(
            @Value("${order.audit.parallelism:2}") int parallelism
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int size = Math.max(1, parallelism);
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(size * 200);
        executor.setThreadNamePrefix("checkout-audit-worker-");
        executor.initialize();
        return executor;
    }
}
