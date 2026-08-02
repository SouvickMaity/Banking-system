package com.banking.paymentservice.dto;

import com.banking.paymentservice.entity.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentOrderResponse {

    private  String paymentId;

    private String razorpayOrderId;

    private BigDecimal amount;

    private String currency;
    private String status;

    private String razorpayKeyId;


}
