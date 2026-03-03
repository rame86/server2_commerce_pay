// config/RabbitMQConfig.java
package com.example.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange: 메시지가 가장 먼저 도착하는 우체국. 라우팅 규칙을 결정함
    public static final String EXCHANGE_NAME = "msa.direct.exchange";
    
    // Queue: 메시지가 소비되기 전까지 저장되는 우편함
    public static final String QUEUE_NAME = "pay.request.queue";
    
    // Routing Key: 특정 큐로 메시지를 보내기 위한 주소(필터)
    public static final String ROUTING_KEY = "pay.request";

    /**
     * Direct Exchange 설정
     * Routing Key가 정확히 일치하는 큐로만 메시지를 전달함
     */
    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    /**
     * Queue 설정
     * durable = true: 브로커(RabbitMQ 서버) 재시작 시에도 큐가 사라지지 않도록 설정
     */
    @Bean
    public Queue queue() {
        return new Queue(QUEUE_NAME, true); 
    }

    /**
     * Binding: 익스체인지와 큐를 연결
     * msa.direct.exchange로 온 메시지 중 pay.request 키를 가진 것만 pay.request.queue로 전달함
     */
    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    /**
     * Message Converter 설정
     * 기본 문자열 대신 JSON 형식으로 직렬화/역직렬화하여 이기종 서비스(Node.js 등) 간 호환성 확보
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}