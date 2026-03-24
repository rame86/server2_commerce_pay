// src/main/java/com/example/payment/service/UserDashboardService.java
package com.example.payment.service;

import com.example.payment.dto.response.UserDetailPaymentResponseDTO;

public interface UserDashboardService {
    UserDetailPaymentResponseDTO getUserDashboardDetail(Long memberId);
}