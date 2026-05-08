package com.connecthub.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ config for payment-service.
 * Publishes to the SAME exchange/queue that auth-service's EmailConsumer listens on.
 * Queue and exchange names must match exactly.
 *
 * IMPORTANT: The ObjectMapper MUST register JavaTimeModule so that
 * LocalDateTime fields (startDate, endDate, cancelledAt) are serialized as
 * ISO-8601 strings — matching the auth-service consumer's expected format.
 */
@Configuration
public class RabbitMQConfig {

    // Must match auth-service constants exactly
    public static final String EMAIL_EXCHANGE    = "connecthub.email.exchange";
    public static final String EMAIL_QUEUE       = "connecthub.email.queue";
    public static final String EMAIL_ROUTING_KEY = "email.send";

    @Bean
    public TopicExchange emailExchange() {
        return new TopicExchange(EMAIL_EXCHANGE);
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE).build();
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, TopicExchange emailExchange) {
        return BindingBuilder.bind(emailQueue).to(emailExchange).with(EMAIL_ROUTING_KEY);
    }

    /**
     * ObjectMapper with JavaTimeModule so LocalDateTime fields
     * in PaymentEmailEvent serialize as ISO-8601 strings (not arrays).
     */
    @Bean
    public ObjectMapper rabbitObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper rabbitObjectMapper) {
        return new Jackson2JsonMessageConverter(rabbitObjectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
