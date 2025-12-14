package com.project.system.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String ANALYSIS_EXCHANGE = "analysis.exchange";
    public static final String ANALYSIS_QUEUE = "analysis.queue";
    public static final String ANALYSIS_DLX = "analysis.exchange.dlx";
    public static final String ANALYSIS_DLQ = "analysis.queue.dlq";

    @Bean
    public Queue notificationQueue() {
        return new Queue("notification.queue", true);
    }

    @Bean
    public TopicExchange analysisExchange() {
        return ExchangeBuilder.topicExchange(ANALYSIS_EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange analysisDeadLetterExchange() {
        return ExchangeBuilder.topicExchange(ANALYSIS_DLX).durable(true).build();
    }

    @Bean
    public Queue analysisQueue() {
        return QueueBuilder.durable(ANALYSIS_QUEUE)
                .withArgument("x-dead-letter-exchange", ANALYSIS_DLX)
                .build();
    }

    @Bean
    public Queue analysisDeadLetterQueue() {
        return QueueBuilder.durable(ANALYSIS_DLQ).build();
    }

    @Bean
    public Binding analysisBinding() {
        return BindingBuilder.bind(analysisQueue()).to(analysisExchange()).with("analysis.#");
    }

    @Bean
    public Binding analysisDlqBinding() {
        return BindingBuilder.bind(analysisDeadLetterQueue()).to(analysisDeadLetterExchange()).with("#");
    }
}
