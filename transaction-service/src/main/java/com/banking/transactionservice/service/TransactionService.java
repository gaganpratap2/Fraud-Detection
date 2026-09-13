package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {


    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String , String> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC ="transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC ="transaction.COMPLETED";
    private static final String TRANSACTION_REFUNDED_TOPIC ="transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC ="fraud.detected";


    /**
     * SAGA STEP 1: INITIATE TRANSFER
     *  DEDUCT FROM SENDER FROM SENDER VIA FEIGN
     *  SAVE TRANSACTION AS PROCESSING
     *  PUBLISH EVENT TO KAFKA FOR FRAUD CHECK
     *  RETURNS
     */

    public TransactionResponse transfer(TransferRequest request) {

        log.info("SAGA START - Transfer: {} -> {} amount: {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());

        // SAGA STEP 1: Deduct from sender
        accountServiceClient.deductAmount(
                request.getSenderAccountNumber(),
                request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved : {}" , savedTransaction.getId());


//        PUBLISH FOR FRAUD CHECK

        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

    kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId() , String.valueOf(event));
    log.info("SAGA STEP 2 -- TRANSACTION INITIATED EVENT PUBLISHED : {}" , savedTransaction.getId());


    return mapToResponse(savedTransaction);
    }


    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new RuntimeException(
                        "Transaction not found: " + transactionId
                )));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository
                .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }


    public TransactionResponse verifyOTP(String transactionId , String otp) {
        log.info("OTP verification for the transaction : {}" , transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        String otpKey = "verification.otp" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if(storedOtp == null) {
            log.info("OTP expired for the transaction : {}" , transactionId);
            compensateTransaction(transaction , "OTP expired - transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }

        if(!storedOtp.equals(otp)) {
            log.warn("Wrong OTP - blocking account and refunded : {}" , transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompesante(transaction ,
                    "Wrong otp entered - transaction Cancelled" +
                    "account blocked for security");

            return mapToResponse(transaction);
        }

        log.info("Otp verified - completed transaction : {}" , transactionId);

        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return mapToResponse(transaction);

    }


    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}",
                transaction.getSenderAccountNumber(),
                transaction.getAmount());

        // CREDIT MONEY BACK TO SENDER SYNCHRONOUSLY
        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount());

        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason +
                " - SAGA Compensation executed, amount refunded at " + LocalDateTime.now());

        transactionRepository.save(transaction);

        // PUBLISH refund event - Notification service will alert user
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC , transaction.getId() , refundEvent.toString());

        log.info("SAGA COMPENSATION COMPLETED - refunded transaction : {}" , transaction.getSenderAccountNumber());
    }


    private void blockAccountAndCompensate(Transaction transaction, String reason) {

        // Publish fraud.detected -> Account Service will block account
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);

        kafkaTemplate.send(
                FRAUD_DETECTED_TOPIC,
                transaction.getSenderAccountNumber(),
                fraudEvent
        );

        log.warn(
                "fraud.detected published - account: {} will be blocked, Kindly contact to the bank",
                transaction.getSenderAccountNumber()
        );

//        SAGA COMPENSATE AND - REFUND SENDER

        compensateTransaction(transaction , reason);
    }



    private void completeTransaction(Transaction transaction) {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription()
        );

        kafkaTemplate.send(
                TRANSACTION_COMPLETED_TOPIC,
                transaction.getId(),
                completedEvent
        );

        log.info(
                "SAGA COMPLETE - Transaction {} completed",
                transaction.getId()
        );
    }



    private TransactionResponse mapToResponse(Transaction transaction) {

        TransactionResponse response = new TransactionResponse();

        response.setId(transaction.getId());

        response.setSenderAccountNumber(
                transaction.getSenderAccountNumber());

        response.setReceiverAccountNumber(
                transaction.getReceiverAccountNumber());

        response.setAmount(transaction.getAmount());

        response.setType(transaction.getType());

        response.setStatus(transaction.getStatus());

        response.setDescription(transaction.getDescription());

        response.setReferenceNumber(transaction.getReferenceNumber());

        response.setFailureReason(transaction.getFailureReason());

        response.setCreatedAt(transaction.getCreatedAt());

        response.setCompletedAt(transaction.getCompletedAt());

        return response;
    }


}


}
