package com.capstone.executor.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Kafka Topic Configuration
 * Creates orders.DLT, trade-events.DLT, market-data.DLT topics and their DLQ variants if they don't exist
 */
@Configuration
public class KafkaTopicConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public KafkaAdmin kafkaAdmin() {
        return new KafkaAdmin(java.util.Collections.singletonMap(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }
    
    // Main topic for order processing
    @Bean
    public NewTopic ordersTopic() {
        return new NewTopic("orders", 3, (short) 1)
            .configs(java.util.Map.of("retention.ms", "604800000")); // 7 days
    }
    
    // Dead-letter queue topic for failed messages
    @Bean
    public NewTopic ordersDlqTopic() {
        return new NewTopic("orders.DLT", 3, (short) 1)
            .configs(java.util.Map.of("retention.ms", "1209600000")); // 14 days (longer retention for analysis)
    }
    
    // Trade events topic for events published by executor
    @Bean
    public NewTopic tradeEventsTopic() {
        return new NewTopic("trade-events", 3, (short) 1)
            .configs(java.util.Map.of("retention.ms", "604800000")); // 7 days
    }
    
    // Market data topic for quote data
    @Bean
    public NewTopic marketDataTopic() {
        return new NewTopic("market-data", 6, (short) 1)
            .configs(java.util.Map.of("retention.ms", "86400000")); // 1 day
    }
}
