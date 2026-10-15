package com.example.identityservice.repository;

import com.example.identityservice.entity.IdentityOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.LocalDateTime;

public interface IdentityOutboxRepository extends JpaRepository<IdentityOutboxEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from IdentityOutboxEvent e where e.published = false order by e.id")
    List<IdentityOutboxEvent> findPending(Pageable pageable);
    long deleteByPublishedTrueAndRoutingKeyAndCreatedAtBefore(String routingKey, LocalDateTime before);
}
