// src/main/java/com/example/payment/service/Event/PaymentBusinessService.java
package com.example.payment.service.Event;

import com.example.payment.dto.event.PaymentEventRequestDTO;
import com.example.payment.dto.service.TransactionDTO;

public interface PaymentBusinessService {
    TransactionDTO executePaymentLogic(PaymentEventRequestDTO dto);

    TransactionDTO executeRefundLogic(PaymentEventRequestDTO dto);
}