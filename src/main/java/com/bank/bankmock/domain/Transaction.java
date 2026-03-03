package com.bank.bankmock.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) 
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String accountNumber;

    @Column(nullable = false)
    private Long amount;

    @Column(length = 100)
    private String merchantName;

    @Column(nullable = false, unique = true, length = 50)
    private String approvalNo;

    @Column(nullable = false, length = 20)
    private String status; // APPROVED, REJECTED, CANCELLED

    @Column(length = 255)
    private String reason; // 승인 성공/실패 사유 (예: "정상", "잔액 부족", "한도 초과")

    @Column(nullable = false)
    private LocalDateTime transactedAt;

    @Builder
    public Transaction(String accountNumber, Long amount, String merchantName, 
                       String approvalNo, String status, String reason, LocalDateTime transactedAt) {
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.merchantName = merchantName;
        this.approvalNo = approvalNo;
        this.status = status;
        this.reason = reason;
        this.transactedAt = transactedAt;
    }
}