package com.example.livetripupdates.config;

import com.example.livetripupdates.event.TripEventLiveDistributor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
public class RedisEventListenerConfig {

    @Bean
    @ConditionalOnProperty(name = "app.events.redis-listener-enabled", havingValue = "true", matchIfMissing = true)
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            TripEventLiveDistributor liveDistributor,
            @Value("${app.events.pubsub-channel:trip-events}") String channel
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener((message, pattern) ->
                        liveDistributor.distribute(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(channel)
        );
        return container;
    }
}
