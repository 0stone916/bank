package com.bank.bankmock.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentResponse {
    private String status;      // APPROVED (성공) / REJECTED (거절)
    private String approvalNo;  // 승인번호 (성공 시에만 생성)
    private String message;     // 상세 메시지 (잔액 부족 등)
}