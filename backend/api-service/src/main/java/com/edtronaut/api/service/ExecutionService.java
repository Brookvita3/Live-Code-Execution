package com.edtronaut.api.service;

import com.edtronaut.api.dto.ExecutionResponse;
import com.edtronaut.api.dto.RunResponse;
import com.edtronaut.api.model.CodeSession;
import com.edtronaut.api.model.Execution;
import com.edtronaut.api.model.ExecutionStatus;
import com.edtronaut.api.repository.ExecutionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final CodeSessionService codeSessionService;
    private final RedisQueueService redisQueueService;
    private final StringRedisTemplate redisTemplate;
    private final int rateLimitSeconds;

    public ExecutionService(ExecutionRepository executionRepository,
                            CodeSessionService codeSessionService,
                            RedisQueueService redisQueueService,
                            StringRedisTemplate redisTemplate,
                            @Value("${app.execution.rate-limit-seconds}") int rateLimitSeconds) {
        this.executionRepository = executionRepository;
        this.codeSessionService = codeSessionService;
        this.redisQueueService = redisQueueService;
        this.redisTemplate = redisTemplate;
        this.rateLimitSeconds = rateLimitSeconds;
    }

    public RunResponse runCode(UUID sessionId) {
        String rateLimitKey = "rate-limit:run:" + sessionId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateLimitKey))) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait " + rateLimitSeconds + " seconds between executions");
        }

        CodeSession session = codeSessionService.getSession(sessionId);

        Execution execution = new Execution();
        execution.setSessionId(session.getId());
        execution.setSourceCode(session.getSourceCode());
        execution.setLanguage(session.getLanguage());
        execution.setStatus(ExecutionStatus.QUEUED);

        execution = executionRepository.save(execution);

        redisQueueService.enqueue(execution.getId().toString());

        redisTemplate.opsForValue().set(rateLimitKey, "1", rateLimitSeconds, TimeUnit.SECONDS);

        return new RunResponse(execution.getId(), execution.getStatus().name());
    }

    public ExecutionResponse getResult(UUID executionId) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        return new ExecutionResponse(
                execution.getId(),
                execution.getStatus().name(),
                execution.getStdout(),
                execution.getStderr(),
                execution.getExecutionTimeMs()
        );
    }
}
