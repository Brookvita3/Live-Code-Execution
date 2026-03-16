package com.edtronaut.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisQueueService {

    @Value("${app.queue.name}")
    private String queueKey;

    private final StringRedisTemplate redisTemplate;

    public RedisQueueService(@Value("${app.queue.name}") String queueKey, StringRedisTemplate redisTemplate) {
        this.queueKey = queueKey;
        this.redisTemplate = redisTemplate;
    }

    public void enqueue(String executionId) {
        redisTemplate.opsForList().leftPush(queueKey, executionId);
    }
}
