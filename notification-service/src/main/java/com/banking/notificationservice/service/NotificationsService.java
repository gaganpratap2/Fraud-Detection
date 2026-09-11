package com.banking.notificationservice.service;


import com.sun.jdi.PrimitiveValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j

public class NotificationsService {

    @KafkaListener(topic = "transaction.otp.generated")
    public void consumeOtpGenerated(
            @Payload Map<String , Object> payload){

        try{
            String accountNumber = (String) payload.get("accountNumber");
            String transactionId = (String) payload.get("transactionId");
            String amount = (String) payload.get("amount").toString();
            String otp = (String) payload.get("otp")
            String reason = (String) payload.get("reason");

            sendAlert( accountNumber ,
                    "Transaction Verification Required",
                    String.format(
                            "Suspicious activity detected on your account. " +
                                    "Reason: %s. " +
                                    "A transaction of %s is pending verification. " +
                                    "Your OTP is: %s. Valid for 5 minutes. " +
                                    "If this wasn't you - ignore this message.",
                    )
            );
        }catch (Exception e){
            log.error("Error sending OTP notification : {}", e.getMessage());
        }
    }


    private void sendAlert(String accountNumber, String subject, String message){

    }
}
