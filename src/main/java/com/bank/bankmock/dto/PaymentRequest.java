package com.bank.bankmock.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private String userId; 
    private String accountNumber; // 계좌번호
    private Long amount;          // 결제 금액
    private String merchantName;  // 가맹점명
}