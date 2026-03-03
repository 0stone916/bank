package com.bank.bankmock.service;

import com.bank.bankmock.domain.Account;
import com.bank.bankmock.domain.AccountRepository;
import com.bank.bankmock.domain.Transaction;
import com.bank.bankmock.domain.TransactionRepository;
import com.bank.bankmock.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class BankService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate = new RestTemplate(); // 다른 서버에 요청 보낼 도구

    /**
     * [추가] 유저 ID로 계좌 정보 조회 (SmartBudget 서버 호출용)
     */
    @Transactional(readOnly = true)
    public Account getAccountByUserId(String userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저의 연동된 계좌가 없습니다."));
    }

    @Transactional
    public String processPayment(PaymentRequest request) {
// 1. 계좌 조회 및 잔액 차감
        Account account = accountRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계좌입니다."));
        
        account.withdraw(request.getAmount()); // 잔액 차감

        // 2. 승인번호 생성
        String approvalNo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 3. [핵심 추가] bank_transaction 테이블에 내역 기록
        // 결제 시점의 시각(transactedAt)이 여기서 결정됩니다.
        Transaction transaction = Transaction.builder()
                .accountNumber(account.getAccountNumber())
                .amount(request.getAmount())
                .merchantName(request.getMerchantName())
                .approvalNo(approvalNo)
                .status("APPROVED") // 성공했으므로 승인 상태
                .transactedAt(LocalDateTime.now()) // 실제 결제 시각 기록
                .build();
        
        transactionRepository.save(transaction);

        // 4. SmartBudget-CMS로 결제 알림 전송 
        sendNotificationToCms(request, approvalNo, transaction.getTransactedAt());

        return approvalNo;
    }

    private void sendNotificationToCms(PaymentRequest request, String approvalNo, LocalDateTime transactedAt) {
        String cmsUrl = "http://localhost:8080/api/v1/noti/payment"; // CMS 주소
        
        // 전송할 데이터 묶기
        Map<String, Object> body = new HashMap<>();
        body.put("userId", request.getUserId());
        body.put("approvalNo", approvalNo);
        body.put("accountNumber", request.getAccountNumber());
        body.put("amount", request.getAmount());
        body.put("merchantName", request.getMerchantName());
        body.put("transactedAt", transactedAt);

        try {
            // CMS로 POST 요청 전송
            restTemplate.postForEntity(cmsUrl, body, String.class);
        } catch (Exception e) {
            System.err.println("CMS 알림 전송 실패: " + e.getMessage());
        }
    }
}