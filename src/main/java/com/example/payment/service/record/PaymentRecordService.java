//src/main/java/com/example/payment/service/PaymentRecordService.java
package com.example.payment.service.record;

import java.util.UUID;

public interface PaymentRecordService {

    /**
     * [결제 승인 성공 후 후속 처리]
     * REQUIRES_NEW: 메인 로직에서 예외가 발생해 롤백되더라도, 성공 이력 자체는 무조건 DB에 남기기 위함.
     */
    void processApprovalSuccess(UUID chargeId, String memberId);

    /**
     * [결제 승인 실패 후 후속 처리]
     * REQUIRES_NEW: 메인 로직에서 예외가 발생해 롤백되더라도, 실패 이력 자체는 무조건 DB에 남기기 위함.
     */
    void processApprovalFail(UUID chargeId, String errorMessage);
}