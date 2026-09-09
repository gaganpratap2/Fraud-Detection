
package com.banking.accountservice.dto;

import com.banking.accountservice.entity.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

    @NotBlank(message = "Account Holder name is required")
    private String accountHolderName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid Email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @NotNull(message = "Initial deposit is required")
    @DecimalMin(
            value = "0.00",
            inclusive = true,
            message = "Initial deposit cannot be negative"
    )
    private BigDecimal initialDeposit;
}
























//package com.banking.accountservice.dto;
//
//
//import com.banking.accountservice.entity.AccountType;
//import jakarta.persistence.Column;
//import jakarta.validation.constraints.Email;
//import jakarta.validation.constraints.NotBlank;
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//import java.math.BigDecimal;
//
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//public class CreateAccountRequest {
//
//    @NotBlank(message = "Account Holder name is required")
//    private String accountHolderName;
//
//    @NotBlank(message = "Email is required")
//    @Email(message = "Invalid Email format")
//    private String email;
//
//
//    private String phone;
//
//    private AccountType  accountType;
//
//    private BigDecimal initialDeposit;
//}
