// src/main/java/com/example/payment/service/Event/SettlementEventService.java
package com.example.payment.service.Event;

import com.example.payment.dto.event.PaymentEventDTO;

public interface SettlementEventService {

    /**
     * 관리자 정산 대시보드 데이터 조회 및 집계 처리
     * * @param dto 메시지 큐를 통해 수신된 이벤트 데이터
     */
    public void processAdminSettlement(PaymentEventDTO dto);

    public void processAdminGetAll(PaymentEventDTO dto);

    public void processAdminArtistDetail(PaymentEventDTO dto);

    public void processAdminSummary(PaymentEventDTO dto);

    public void processAdminUserDetail(PaymentEventDTO dto);

}