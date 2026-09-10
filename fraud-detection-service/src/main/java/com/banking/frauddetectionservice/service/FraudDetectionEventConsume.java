package com.banking.frauddetectionservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionEventConsume {

    private final FraudDetectionService  fraudDetectionService;

    /**
     * LISTENS TRANSACTION.INITIATED TOPIC
     * EVERY TRANSACTION WILL GO THROUGH FRAUD CHECK BEFORE COMPLETING.
     * @param payload
     */


    @KafkaListener(topics = "transaction.initiated" , groupId = "fraud-detection-group")
    public void consumeTransactionInitiated(@Payload Map<String , Object> payload){
        log.info(("Received transaction For fraud check : {}"),
                payload.get("transactionId"));

        try{
            fraudDetectionService.chechTransaction(payload);
        }catch (Exception e){

        }
    }
}
