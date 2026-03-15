package com.bank.bankmock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final String TOPIC = "payment-notif";

    public void sendNotification(String payload) throws InterruptedException, ExecutionException {
        log.info("Kafka 메시지 전송 시작: {}", payload);
        
        // KafkaTemplate을 이용해 비동기로 메시지를 던집니다.
        kafkaTemplate.send(TOPIC, payload).get();
    }
}