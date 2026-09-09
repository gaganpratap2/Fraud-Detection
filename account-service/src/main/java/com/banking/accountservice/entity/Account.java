package com.banking.accountservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(nullable = false)
    private String accountHolderName;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String accountHolderNumber;

    @NotBlank
    @Email
    @Column(nullable = false)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String phone;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyTransactionLimit;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}



//package com.banking.accountservice.entity;
//
//import jakarta.persistence.*;
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//import org.hibernate.annotations.CreationTimestamp;
//import org.hibernate.annotations.UpdateTimestamp;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//
//
//@Entity
//@Table(name = "account")
//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//public class Account {
//    @Id
//    @GeneratedValue(strategy = GenerationType.UUID);
//    private String id;
//
//    @Column(nullable = false)
//    private String accountHolderName;
//
//    @Column(nullable = false , unique = true)
//    private String accountHolderNumber;
//
//    @Column(nullable = false)
//    private String email;
//
//    @Column(nullable = false)
//    private String phone;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private AccountType accountType;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private AccountStatus accountStatus;
//
//    @Column(nullable = false , precision = 15 , scale = 2)
//    private BigDecimal balance;
//
//    @Column(nullable = false , precision = 15 , scale = 2)
//    private BigDecimal dailyTransactionLimit;
//
//    @CreationTimestamp
//    private LocalDateTime createdAt;
//
//    @UpdateTimestamp
//    private LocalDateTime updatedAt;
//}

