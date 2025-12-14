package com.project.system.mq;

import com.project.system.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class AnalysisEventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${analysis.version:v1}")
    private String version;

    public void publish(String action, Map<String, Object> payload) {
        if (action == null || action.isBlank()) return;

        Supplier<Void> sendTask = () -> {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("source", "system-service");
            envelope.put("action", action);
            envelope.put("payload", payload == null ? Map.of() : payload);
            envelope.put("version", version);
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.ANALYSIS_EXCHANGE, action, envelope);
            } catch (Exception e) {
                System.err.println("[AnalysisEvent] send failed: " + e.getMessage());
            }
            return null;
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendTask.get();
                }
            });
        } else {
            sendTask.get();
        }
    }
}
