package com.example.payment.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/payment")
@RestController
public class PaymentController {
    
    @GetMapping("/")
    public String hello() {
        return "서버가 정상적으로 실행 중!";
    }

    
}
