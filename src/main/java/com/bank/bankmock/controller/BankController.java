package com.bank.bankmock.controller;

import com.bank.bankmock.domain.Account;
import com.bank.bankmock.dto.PaymentRequest;
import com.bank.bankmock.dto.PaymentResponse;
import com.bank.bankmock.service.BankService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    // 계좌 정보 및 잔액 조회 (SmartBudget 서버에서 호출)
    @GetMapping("/accounts")
    public ResponseEntity<Account> getAccountInfo(@RequestParam String userId) {
        // 서비스에서 user_id로 계좌 정보를 찾아 DTO로 반환
        Account accountInfo = bankService.getAccountByUserId(userId);
        
        if (accountInfo == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(accountInfo);
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> requestPayment(@RequestBody PaymentRequest request) {
        System.out.print(request.getUserId());
        try {
            // 서비스 호출하여 결제 진행
            bankService.processPayment(request);
            
            // 성공 시: 승인번호 생성 및 응답
            String approvalNo = UUID.randomUUID().toString().substring(0, 8); 
            return ResponseEntity.ok(new PaymentResponse("APPROVED", approvalNo, "결제가 완료되었습니다."));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(new PaymentResponse("REJECTED", null, e.getMessage()));
        }
    }
}