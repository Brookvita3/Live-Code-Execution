package com.edtronaut.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisQueueService {

    private static final Logger log = LoggerFactory.getLogger(RedisQueueService.class);

    @Value("${app.queue.name}")
    private String queueKey;

    private final StringRedisTemplate redisTemplate;

    public RedisQueueService(@Value("${app.queue.name}") String queueKey, StringRedisTemplate redisTemplate) {
        this.queueKey = queueKey;
        this.redisTemplate = redisTemplate;
    }

    public void enqueue(String executionId) {
        log.debug("Pushing executionId {} to queue {}", executionId, queueKey);
        redisTemplate.opsForList().leftPush(queueKey, executionId);
    }
}
