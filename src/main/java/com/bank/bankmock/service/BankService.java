package com.bank.bankmock.service;

import com.bank.bankmock.domain.Account;
import com.bank.bankmock.domain.AccountRepository;
import com.bank.bankmock.domain.Outbox;
import com.bank.bankmock.domain.OutboxRepository;
import com.bank.bankmock.domain.OutboxStatus;
import com.bank.bankmock.domain.Transaction;
import com.bank.bankmock.domain.TransactionRepository;
import com.bank.bankmock.dto.PaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Slf4j
@Service
@RequiredArgsConstructor
public class BankService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository; 
    private final ObjectMapper objectMapper;
    private final NotificationProducer notificationProducer;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 유저 ID로 계좌 정보 조회
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
        LocalDateTime transactedAt = LocalDateTime.now();

        // 3. bank_transaction 기록
        Transaction transaction = Transaction.builder()
                .accountNumber(account.getAccountNumber())
                .amount(request.getAmount())
                .merchantName(request.getMerchantName())
                .approvalNo(approvalNo)
                .status("APPROVED")
                .transactedAt(transactedAt)
                .build();
        transactionRepository.save(transaction);

        // 4. Outbox에 저장
        String payload = createPayload(request, approvalNo, transactedAt);
        outboxRepository.save(new Outbox("PAYMENT_NOTIFICATION", approvalNo, payload));

        // 5. 직접 호출 대신 이벤트를 발행 (Publish)
        eventPublisher.publishEvent(payload); 

        return approvalNo;
    }

    private String createPayload(PaymentRequest request, String approvalNo, LocalDateTime transactedAt) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("userId", request.getUserId());
            data.put("accountNumber", request.getAccountNumber());
            data.put("amount", request.getAmount());
            data.put("merchantName", request.getMerchantName()); 
            data.put("approvalNo", approvalNo); 
            data.put("transactedAt", transactedAt.toString()); 
            data.put("type", "PAYMENT_CONFIRMED");

            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("Payload 생성 실패", e);
        }
    }

    // 커밋 성공 시에만 실행
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 새로운 트랜잭션에서 상태 업데이트
    public void handlePaymentCofirmed(String payload) {
        
        try {
            // 1. Kafka 전송
            notificationProducer.sendNotification(payload);
            
            // 2. 전송 성공 시 Outbox 상태 업데이트 (DONE)
            updateOutboxStatus(payload, OutboxStatus.DONE);
            
        } catch (Exception e) {
            // 3. 실패 시 상태 업데이트 (FAILED)
            updateOutboxStatus(payload, OutboxStatus.FAILED);
            log.error("Kafka 전송 실패로 인한 Outbox 상태 업데이트: {}", e.getMessage());
        }
    }

    private void updateOutboxStatus(String payload, OutboxStatus status) {
        try {
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            String approvalNo = (String) map.get("approvalNo");
            
            outboxRepository.findByAggregateId(approvalNo)
                .ifPresent(outbox -> {
                    if (status == OutboxStatus.DONE) outbox.markAsDone();
                    else outbox.markAsFailed();
                });
        } catch (Exception e) {
            log.error("Outbox 상태 업데이트 중 오류 발생", e);
        }
    }
}