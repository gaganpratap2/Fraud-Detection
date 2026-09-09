package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private static final SecureRandom secureRandom = new SecureRandom();

    public AccountResponse createAccount(CreateAccountRequest request) {

        log.info("Creating account for: {}", request.getEmail());

        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException(
                    "Account already exists for email: " + request.getEmail()
            );
        }

        Account account = new Account();

        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());

        // Generate account number
        account.setAccountHolderNumber(generateAccountNumber());

        account.setDailyTransactionLimit(
                request.getAccountType() == AccountType.SAVING
                        ? new BigDecimal("1000000")
                        : new BigDecimal("500000")
        );

        // Save account
        Account savedAccount = accountRepository.save(account);

        log.info("Account Created: {}", savedAccount.getAccountHolderNumber());

        return mapToResponse(savedAccount);
    }

    private AccountResponse mapToResponse(Account account) {

        AccountResponse response = new AccountResponse();

        response.setId(account.getId());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setAccountHolderNumber(account.getAccountHolderNumber());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setAccountStatus(account.getAccountStatus());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt());
        response.setUpdatedAt(account.getUpdatedAt());

        return response;
    }


    private String generateAccountNumber() {

        String accountNumber;

        do {
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);

        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    public AccountResponse getAccount(String accountNumber) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        return account.getBalance();
    }

    /**
     * BLOCK ACCOUNT -- CALLED BY FRAUD DETECTION SERVICE VIA KAFKA
     */
    public void blockAccount(String accountNumber) {

        log.info("Blocking account for: {}", accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setAccountStatus(AccountStatus.BLOCKED);

        accountRepository.save(account);

        log.info("Account blocked for: {}", accountNumber);
    }

    public void deductBalance(String accountNumber, BigDecimal amount) {

        log.info("Deducting balance {} from account: {}", amount, accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException(
                    "Account is not active " + accountNumber
            );
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException(
                    "Insufficient funds for account " + accountNumber
            );
        }

        account.setBalance(
                account.getBalance().subtract(amount)
        );

        accountRepository.save(account);

        log.info(
                "Balance updated, new Balance: {}",
                account.getBalance()
        );
    }

    public void creditBalance(String accountNumber, BigDecimal amount) {

        log.info("Crediting {} to account: {}", amount, accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        account.setBalance(
                account.getBalance().add(amount)
        );

        accountRepository.save(account);

        log.info(
                "Balance Credited. New Balance: {}",
                account.getBalance()
        );
    }
}
