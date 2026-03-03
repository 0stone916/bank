package com.bank.bankmock.domain;


import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    // Optional<Transaction> findByUserAccountNumber(Transaction transaction);

}