package com.banking.paymentservice.service;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.entity.PaymentStatus;
import com.banking.paymentservice.repository.PaymentRepository;
import jakarta.persistence.criteria.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.util.JSONPObject;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    private static final String  PAYMENT_COMPLETE_TOPIC = "payment.completed";
    private static final String  PAYMENT_FAILED_TOPIC = "payment.failed";


    /**
     * Create order in razorpay
     * save payment record in db
     * Return order details to frontend     *
     * FrontEnd show Razorpay checkout
     * user pay
     * razorpay calls webhook
     * @param req
     * @return
     * @throws RazorpayException
     */




    public PaymentOrderResponse createPaymentOrder(CreatePaymentRequest req)
                                                                            throws RazorpayException {

            log.info("Creating Payment Order for account : {} amount {} ",
                    req.getAccountNumber(), req.getAmount());


            RazorpayClient razorpayClient = new RazorpayClient(keyId , keySecret);

            int convertedAmount =  req.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .intValue();

        JSONPObject orderRequest = new JSONPObject();
        orderRequest.put("amount", convertedAmount);
        orderRequest.put("currency", "USD/INR");
        orderRequest.put("receipt", "rcpt_" + System.currentTimeMillis() + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 10));

        Order razorpayOrder = razorpayClient.orders.create(orderRequest);

        log.info("Razorpay order created: {}", razorpayOrder.get("id").toString());


//        SAVE PAYMENT RECORD;

        Payment payment = new Payment();
        payment.setRazorpayOrderId(razorpayOrder.get("id").toString());
        payment.setAccountNumber(req.getAccountNumber());
        payment.setAmount(req.getAmount());
        payment.setCurrency("USD/INR");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setDescription(req.getDescription());

        Payment savedPayment = paymentRepository.save(payment);

        return new PaymentOrderResponse(
                savedPayment.getId(),
                razorpayOrder.get("id").toString(),
                req.getAmount(),
                "USD/INR",
                "CREATED",
                keyId
        );






    }











}
