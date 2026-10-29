package com.example.livetripupdates.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTripEventPublisher implements TripEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channel;

    public RedisTripEventPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.events.pubsub-channel:trip-events}") String channel
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.channel = channel;
    }

    @Override
    public void publish(TripEventEnvelope envelope) {
        try {
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Event envelope could not be serialized.", exception);
        }
    }
}
