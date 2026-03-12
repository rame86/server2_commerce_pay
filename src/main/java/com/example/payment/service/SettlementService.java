package com.example.payment.service;

import com.example.payment.dto.event.PaymentEventDTO;

public interface SettlementService {
    public void processSettlement(PaymentEventDTO dto);

}
