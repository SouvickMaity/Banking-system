package com.banking.transectionservice.entity;

public enum TransactionStatus {
    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    FAILED,
    FLAGGED,
    COMPLETED
}
