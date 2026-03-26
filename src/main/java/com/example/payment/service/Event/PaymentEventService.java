// src/main/java/com/example/payment/service/Event/PaymentEventService.java
package com.example.payment.service.Event;

import com.example.payment.dto.event.PaymentEventDTO;

/**
 * [결제 이벤트 처리 서비스 인터페이스]
 * 외부 메시지 브로커(Kafka, RabbitMQ 등)로부터 수신한 비동기 이벤트를 처리함.
 * 도메인(주문, 아티스트 등) 간의 결합도를 낮추고 데이터 일관성을 맞추는 핵심 통로.
 */
public interface PaymentEventService {
    
    /**
     * [결제 완료 이벤트 처리]
     * 사용자의 구매가 확정되었을 때 호출됨.
     * 지갑 잔액을 차감하고, 향후 아티스트에게 지급할 정산 원장(Ledger)을 생성함.
     */
    void processPaymentEvent(PaymentEventDTO dto);

    /**
     * [환불 이벤트 처리]
     * 결제 취소나 환불 요청이 발생했을 때 호출됨.
     * 차감되었던 지갑 잔액을 원상 복구하고, 기존 정산 원장의 상태를 무효화(REFUNDED)함.
     */
    void processRefundEvent(PaymentEventDTO dto);

    /**
     * [후원 이벤트 처리]
     * 아티스트 대상 후원 결제 건을 처리함.
     * 일반 티켓/상품 결제와 달리 후원 전용 수수료 정책이 적용됨.
     */
    void processDonationEvent(PaymentEventDTO dto);

    /**
     * [아티스트 정산 요청 처리]
     * 주기적 정산이나 수동 정산 요청 이벤트 발생 시 호출됨.
     * 확정된 수익 내역을 계산하여 아티스트의 실제 출금 가능 잔액으로 전환함.
     */
    void processArtistSettlementRequest(PaymentEventDTO dto);

    /**
     * [아티스트 지갑 생성 처리]
     * 새로운 아티스트가 시스템에 등록(가입/승인)되었을 때 호출됨.
     * 해당 아티스트가 정산금을 받을 수 있도록 전용 계좌(ArtistAccount)를 초기화함.
     */
    void processArtistWalletCreate(PaymentEventDTO dto);
}