package com.banking.transactionservice.service;

import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer{
    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String , String > redisTemplate;
    private final TransactionService transactionService;
    private static final long OTP_EXPIRE_MINUTES = 5;

    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * consume verification.required
     * generate OTP ans user to verify
     * @param payload
     */

    @KafkaListener(topics = "verification.required")
    public void consumeVerificationRequired(
            @Payload Map<String, Object> payload) {

        try {

            String transactionId =
                    (String) payload.get("transactionId");

            String accountNumber =
                    (String) payload.get("accountNumber");

            String reason =
                    (String) payload.get("reason");

            log.info(
                    "Verification required - transaction: {} reason: {}",
                    transactionId,
                    reason
            );

            Transaction transaction = transactionRepository.findById(transactionId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Transaction not found "
                                                    + transactionId
                                    )
                            );

            // Only PROCESSING transactions should continue
            if (transaction.getStatus() != TransactionStatus.PROCESSING) {

                log.warn(
                        "Transaction {} is not PROCESSING. Current status: {}",
                        transactionId,
                        transaction.getStatus()
                );

                return;
            }

//           generate 6 digit otp
            String otp = String.format("%06d" , (int)(Math.random() * 900000) + 100000);

//            store otp in redis
            String otpKey = "verification:otp" + transactionId;
            redisTemplate.opsForValue().set(otpKey , otp , OTP_EXPIRE_MINUTES , TimeUnit.MINUTES);

            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);

            log.info("OTP generated fro transaction : {} expires in {} min" , transactionId , OTP_EXPIRE_MINUTES );


//            NOTIFY USER

            Map<String , Object> otpEvent = new HashMap<>();
            otpEvent.put("otp", otp);
            otpEvent.put("transactionId", transactionId);
            otpEvent.put("accountNumber", accountNumber);
            otpEvent.put("reason", reason);
            otpEvent.put("amount", payload.get("amount"));

            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC , transactionId , otpEvent);

        } catch (Exception e) {

            log.error(
                    "Error handling verification required transaction: {}",
                    e.getMessage()
            );
        }
    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCheckResult(
            @Payload Map<String, Object> payload ){
        try {
            String transactionId = (String) payload.get("transactionId");
            transactionService.processCleanResult(transactionId);

        }catch (Exception e){
            log.error("Error processing fraud check result : {}", e.getMessage());
        }
    }



}