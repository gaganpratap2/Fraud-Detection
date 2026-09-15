package com.banking.paymentservice.controller;


import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.repository.PaymentRepository;
import com.banking.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderResponse> createPaymentOrder(
            @Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPaymentOrder(request);

    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @Valid Map<String , Object> payload) {

        paymentService.handleWebhook(payload);

        return ResponseEntity.status("Webhook Processed");


    }













}
