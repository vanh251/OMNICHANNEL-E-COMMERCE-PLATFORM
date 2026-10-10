package com.example.orderinventoryservice.repository;

import com.example.orderinventoryservice.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findTop20ByPublishedFalseOrderByIdAsc();
}
