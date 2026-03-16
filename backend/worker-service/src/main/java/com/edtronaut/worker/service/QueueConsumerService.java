package com.edtronaut.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class QueueConsumerService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QueueConsumerService.class);

    @Value("${app.queue.name}")
    private String queueKey;

    @Value("${app.worker.max-retries}")
    private int maxRetries;

    private final StringRedisTemplate redisTemplate;
    private final CodeExecutorService codeExecutorService;

    public QueueConsumerService(@Value("${app.queue.name}") String queueKey,
                                @Value("${app.worker.max-retries}") int maxRetries,
                                StringRedisTemplate redisTemplate,
                                CodeExecutorService codeExecutorService) {
        this.queueKey = queueKey;
        this.maxRetries = maxRetries;
        this.redisTemplate = redisTemplate;
        this.codeExecutorService = codeExecutorService;
    }

    @Override
    public void run(String... args) {
        log.info("Worker started. Listening for jobs on '{}'...", queueKey);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // BRPOP blocks until a job is available (timeout 0 = block indefinitely,
                // but we use 5s to allow graceful shutdown checks)
                String executionId = redisTemplate.opsForList()
                        .rightPop(queueKey, Duration.ofSeconds(5));

                if (executionId == null) {
                    continue; // timeout — loop back and check interruption
                }

                log.info("Job picked: execution_id={}", executionId);
                executeWithRetry(UUID.fromString(executionId));

            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Worker interrupted, shutting down.");
                    break;
                }
                log.error("Error in queue consumer loop", e);
                sleep(1000); // backoff before retrying the loop
            }
        }
    }

    private void executeWithRetry(UUID executionId) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                codeExecutorService.execute(executionId);
                return; // success
            } catch (Exception e) {
                log.warn("Attempt {}/{} failed for execution {} (infrastructure error)",
                        attempt, maxRetries, executionId, e);
                if (attempt < maxRetries) {
                    sleep(1000 * attempt); // linear backoff
                } else {
                    log.error("All {} retries exhausted for execution {}", maxRetries, executionId);
                }
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
