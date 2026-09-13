package com.banking.transactionservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionCompletedEvent {
    String transactionId;
    String description;
    String senderAccountNumber;
    String receiverAccountNumber;
    String amount;
}
