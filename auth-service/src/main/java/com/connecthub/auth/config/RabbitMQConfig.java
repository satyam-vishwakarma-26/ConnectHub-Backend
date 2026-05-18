package com.connecthub.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure: exchange, queue, binding, and JSON serializer.
 *
 * IMPORTANT: TypePrecedence.INFERRED is used so the consumer always
 * deserializes
 * the message body using the @RabbitListener method parameter type
 * (EmailEvent),
 * regardless of the __TypeId__ header sent by any producer (e.g.
 * payment-service).
 * This allows cross-service messaging without sharing DTO classes.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EMAIL_EXCHANGE = "connecthub.email.exchange";
    public static final String EMAIL_QUEUE = "connecthub.email.queue";
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
        return BindingBuilder
                .bind(emailQueue)
                .to(emailExchange)
                .with(EMAIL_ROUTING_KEY);
    }

    /**
     * ObjectMapper with JavaTimeModule so LocalDateTime fields
     * in EmailEvent serialize/deserialize correctly.
     */
    @Bean
    public ObjectMapper rabbitObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Jackson converter configured with INFERRED type precedence.
     * The consumer's method signature (EmailEvent parameter) determines
     * the deserialization target — NOT the __TypeId__ header from the producer.
     */
    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper rabbitObjectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(rabbitObjectMapper);

        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.addTrustedPackages("*");

        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
