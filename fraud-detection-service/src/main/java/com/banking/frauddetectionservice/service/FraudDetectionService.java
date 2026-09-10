package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.chech.clean";

    public void checkTransaction(Map<String , Object> payload) {
        String transactionId = (String) payload.get("transactionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = (BigDecimal) payload.get("amount");


//    fetch real balance from account service

        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);

        log.info("Checking Transaction : {} account : {} amount : {} balance: {}",
                transactionId , accountNumber, amount , senderBalance);

        FraudCheckResult res = performFraudCheck(accountNumber , amount , senderBalance);

        if(res.isFraud()){
            log.info("Suspicious activity detected - amount: {}" +
                    "reason : {} - requesting OTP verification" ,
                    accountNumber , res.getReason());

            Map<String , Object> verificationEvent = new HashMap<>();

            verificationEvent.put("transactionId", transactionId);
            verificationEvent.put("senderAccountNumber", accountNumber);
            verificationEvent.put("amount", amount);
            verificationEvent.put("reason", res.getReason());


            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC, transactionId , verificationEvent);
        }
        else{
//            transaction is cleaned

            log.info("Transaction clean");

            Map<String , Object> transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionId", transactionId);
            transactionCleanEvent.put("isFraud" , false);
            transactionCleanEvent.put("reason", null);


            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC , transactionId , transactionCleanEvent);

        }
    }


}
