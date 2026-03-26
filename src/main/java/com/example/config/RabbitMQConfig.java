// config/RabbitMQConfig.java
package com.example.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * [RabbitMQ 인프라 설정]
 * MSA 환경에서 서비스 간 비동기 통신을 위한 Exchange, Queue, Binding을 정의함.
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    /*
     * 결제와 환불 모두 하나의 큐에서 구독중
     * payload.type(PAYMENT or REFUND)에 따라 결제 환불 구분
     */

    /**
     * Exchange: 메시지가 가장 먼저 도착하는 라우팅 허브.
     * msa.direct.exchange: Direct 타입을 사용하여 Routing Key가 정확히 일치하는 곳으로 배달.
     */
    public static final String EXCHANGE_NAME = "msa.direct.exchange";

    /**
     * Routing Key: 메시지의 목적지를 식별하는 주소 값.
     */
    public static final String ROUTING_KEY = "pay.request";

    /**
     * Queue: 최종적으로 메시지가 쌓이는 우편함.
     * 소비자(Listener)는 이 큐를 구독하여 메시지를 가져감.
     */
    public static final String QUEUE_NAME = "pay.request.queue";

    /**
     * [Direct Exchange 빈 등록]
     * 전달된 Routing Key와 큐의 Binding Key가 1:1로 매칭될 때만 메시지를 전달함.
     */
    @Bean
    public DirectExchange exchange() {
        log.info("[RabbitMQ_CONFIG] Exchange 생성: {}", EXCHANGE_NAME);
        return new DirectExchange(EXCHANGE_NAME);
    }

    /**
     * [Queue 빈 등록]
     * durable = true: RabbitMQ 서버가 재시작되어도 큐 데이터가 손실되지 않도록 디스크에 저장함.
     */
    @Bean
    public Queue queue() {
        log.info("[RabbitMQ_CONFIG] Queue 생성: {}", QUEUE_NAME);
        return new Queue(QUEUE_NAME, true);
    }

    /**
     * [Binding 설정]
     * Exchange와 Queue를 특정 Routing Key로 연결함.
     * 'msa.direct.exchange'로 들어온 메시지 중 'pay.request' 키를 가진 메시지만
     * 'pay.request.queue'로 전달.
     */
    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        log.info("[RabbitMQ_CONFIG] Binding 설정: {} -> {} (Key: {})",
                EXCHANGE_NAME, QUEUE_NAME, ROUTING_KEY);
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    /**
     * [메시지 컨버터 설정]
     * 객체를 JSON으로 직렬화/역직렬화함.
     * Java 객체를 메시지로 보낼 때 자동으로 JSON 문자열로 변환하여 언어가 다른 서비스 간의 통신 호환성을 확보함.
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        log.info("[RabbitMQ_CONFIG] Jackson2JsonMessageConverter 빈 등록 완료");
        return new Jackson2JsonMessageConverter();
    }
}