// src/main/java/com/example/payment/service/PaymentServiceImpl.java
package com.example.payment.service;

import org.springframework.stereotype.Service;

import com.example.payment.mapper.PaymentMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;


    
}