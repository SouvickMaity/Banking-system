package com.banking.transectionservice.dto;

import com.banking.transectionservice.entity.TransactionStatus;
import com.banking.transectionservice.entity.TransactionType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class TransferRequest {

    @NotBlank(message = "sender account number is required")
    private String senderAccountNumber;

    @NotBlank(message = "receiver account number is required")
    private String receiverAccountNumber;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;


    private String description;

}


