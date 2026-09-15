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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final kafkaTemplate<String , Object> kafkaTemplate;

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

    public void handleWebhook(Map<String, Object> payload) {
        log.info("Received Razorpay webhook: {}", payload.get("event"));

        String event = (String) payload.get("event");

        if ("payment.captured".equals(event)) {
            handlePaymentSuccess(payload);
        } else if ("payment.failed".equals(event)) {
            handlePaymentFailure(payload);
        }
    }


    private void handlePaymentSuccess(Map<String, Object> payload) {
        try {
            Map<String, Object> paymentData = extractPaymentData(payload);

            String orderId = (String) paymentData.get("order_id");
            String paymentId = (String) paymentData.get("id");

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException(
                            "Payment not found for order: " + orderId
                    ));

            payment.setRazorpayPaymentId(paymentId);
            payment.setStatus(PaymentStatus.COMPLETED);

            paymentRepository.save(payment);

//            publish payment completed event to kafka

            Map<String , Object> event = new HashMap<>();

            event.put("paymentId", payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("razorpayOrderId", paymentId);

            kafkaTemplate.send(PAYMENT_COMPLETE_TOPIC , payment.getId() , event);

            log.info("Payment completed {}" , payment.getId());



        } catch (Exception e) {
            // Handle exception
            log.info("Error handling payment success : {}" , e.getMessage());
        }
    }


    private void handlePaymentFailure(Map<String, Object> payload) {
        try {
            Map<String, Object> paymentData = extractPaymentData(payload);

            String orderId = (String) paymentData.get("order_id");

            Payment payment = paymentRepository
                    .findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException(
                            "Payment not found for order: " + orderId
                    ));

            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment failed via Razorpay");

            paymentRepository.save(payment);

            // Publish payment failed event
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("reason", "Payment failed via Razorpay");

            kafkaTemplate.send(PAYMENT_FAILED_TOPIC , payment.getId() , event);

            log.warn("Payment failed {}" , payment.getId());
            // publish event here

        } catch (Exception e) {
            // log the exception
            log.error("Error handling payment success : {}" , e.getMessage());
        }
    }



    private Map<String, Object> extractPaymentData(Map<String, Object> payload) {
       Map<String , Object> entity = (Map<String , Object>) payload.get("payload");

       Map<String , Object> paymentWrapper = (Map<String , Object>) entity.get("payment");

       return (Map<String, Object>) paymentWrapper.get("entity");
    }












}
