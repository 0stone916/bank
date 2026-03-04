package com.bank.bankmock.domain;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;
    private String aggregateId;   

    @Column(columnDefinition = "TEXT") 
    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status; 

    private LocalDateTime createdAt;

    public Outbox(String aggregateType, String aggregateId, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxStatus.INIT; 
        this.createdAt = LocalDateTime.now();
    }

    public void markAsDone() {
        this.status = OutboxStatus.DONE;
    }

    public void markAsFailed() {
        this.status = OutboxStatus.FAILED;
    }
}