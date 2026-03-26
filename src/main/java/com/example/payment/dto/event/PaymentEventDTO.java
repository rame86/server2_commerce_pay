// src/main/java/com/example/payment/dto/event/PaymentEventDTO.java
package com.example.payment.dto.event;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * [결제/정산 통합 이벤트 DTO]
 * MSA 환경에서 서비스 간(Shop, Settlement 등) 통신에 사용되는 요청 페이로드.
 * 'type'에 따라 필수값이 달라지며, RabbitMQ를 통해 전달됨.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class PaymentEventDTO {

    /*
     * [MQ 통신 규격]
     * Exchange: "msa.direct.exchange"
     * Request Queue: "pay.request.queue"
     * Reply: 비동기 응답을 위해 'replyRoutingKey' 필드에 반환 주소 필수 기입
     */

    // === [공통 및 식별 정보] ===
    /** 이벤트 유형 (PAYMENT: 결제, REFUND: 환불, DONATION: 후원, ADMIN: 관리자 조회, ARTIST_APPROVE: 아티스트 승인) */
    private String type; 
    
    /** 주문/예약 번호 (환불/정산 시 원본 거래 식별 키, 관리자 요청 시 기능 구분자로도 활용) */
    private String orderId; 
    
    /** 사용자(구매자/후원자) 식별 ID */
    private Long memberId; 
    
    /** 처리가 완료된 후 결과를 송신할 RabbitMQ 라우팅 키 */
    private String replyRoutingKey; 

    // === [금액 및 수량 정보] ===
    /** 실제 결제 또는 환불되는 최종 변동 금액 */
    private BigDecimal amount; 
    
    /** 할인 적용 전 원래 가격 (정산 시 원가 계산용) */
    private BigDecimal originalPrice; 
    
    /** 구매 또는 예매 수량 (기본값 1) */
    private Integer quantity; 
    
    /** 플랫폼 수수료율 (정산 시 공제할 퍼센트 단위) */
    private BigDecimal fee; 
    
    /** 물류/배송 비용 (상품 결제 시 사용) */
    private BigDecimal shippingFee; 

    // === [메타 데이터] ===
    /** 후원/정산 대상 아티스트 ID */
    private Long artistId; 
    
    /** 거래 적요 (공연명, 상품명 등 원장에 기록될 제목) */
    private String eventTitle; 
    
    /** 아티스트 이름 (지갑 생성 또는 정산 리포트용) */
    private String artistName; 

    // === [관리자용 벌크 데이터] ===
    /** 다수 회원의 정보 일괄 요청 시 사용하는 ID 리스트 */
    private List<Long> allMemberId; 
    
    /** 다수 아티스트의 정산 내역 일괄 요청 시 사용하는 ID 리스트 */
    private List<Long> allArtistId; 
}