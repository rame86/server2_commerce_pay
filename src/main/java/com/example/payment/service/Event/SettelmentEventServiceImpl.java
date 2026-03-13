package com.example.payment.service.Event;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.dto.response.SettlementDTO;
import com.example.payment.messaging.producer.PaymentEventProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettelmentEventServiceImpl {
    private final PaymentEventProducer producer;
    @Lazy
    @Autowired
    private SettelmentEventServiceImpl self;

    @Transactional(readOnly = true) // 데이터베이스 읽기 전용으로 트랜잭션 최적화
    public void processSettlement(PaymentEventDTO dto) {
        String replyKey = dto.getReplyRoutingKey();
        String orderId = dto.getOrderId(); // 정산 조회 요청 고유 ID라고 가정
        
        try {
            // 1. 상태 업데이트 발송 (선택사항이나 일관성을 위해 유지)
            producer.sendDataResponse(replyKey, orderId, "PROCESSING", "정산 데이터 조회 중입니다.", dto.getType(), null);

            // 2. DB에서 정산 내역 리스트 조회 (DTO 매핑)
            // dto.getArtistId() 등 요청자에 맞는 식별자가 필요합니다.
            /* List<SettlementDTO> settlementList = settlementLedgerRepository.findByArtistId(dto.getArtistId())
                .stream()
                .map(entity -> SettlementDTO.builder()
                    .ledgerId(entity.getLedgerId())
                    .artistId(entity.getArtistId())
                    .orderId(entity.getOrderId())
                    .revenueType(entity.getRevenueType())
                    .grossAmount(entity.getGrossAmount())
                    .feeAmount(entity.getFeeAmount())
                    .netAmount(entity.getNetAmount())
                    .status(entity.getStatus())
                    .eventTitle(entity.getEventTitle())
                    .createdAt(entity.getCreatedAt())
                    .build())
                .toList(); 
            */
            
            // 테스트용 빈 리스트 (실제 적용 시 위 주석 해제 및 Repository 연결)
            List<SettlementDTO> settlementList = java.util.Collections.emptyList();

            // 3. 페이로드(리스트)를 포함하여 결과 응답 발송
            producer.sendDataResponse(replyKey, orderId, "COMPLETE", "정산 내역 조회 성공", dto.getType(), settlementList);
            log.info("[SETTLEMENT] 조회 완료 및 응답 발송 - 요청 ID: {}", orderId);

        } catch (Exception e) {
            log.error("[SETTLEMENT] 처리 실패 - 요청 ID: {}, 사유: {}", orderId, e.getMessage());
            producer.sendDataResponse(replyKey, orderId, "FAIL", "정산 데이터 조회 실패: " + e.getMessage(), "ERROR",null);
        }
    }
}
